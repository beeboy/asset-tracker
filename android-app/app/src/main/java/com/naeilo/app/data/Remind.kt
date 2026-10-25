package com.naeilo.app.data

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.naeilo.app.AppModel
import com.naeilo.app.Tab
import com.naeilo.app.core.Config
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.calmSpeaker
import com.naeilo.app.core.calc.goalMonthSteps
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.cformat
import com.naeilo.app.net.Sync
import com.naeilo.app.ui.settings.SettingsRoute
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.analysis.AnalysisRoute
import com.naeilo.app.ui.analysis.MacroData
import com.naeilo.app.ui.analysis.myPathGapToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 홈 리마인드: 앱의 단계·달성, 일정, 내 종목 분석, 설정·구독·새 소식을 푸시 알림 없이 홈에서만 보여 준다.
// 쉼터 친구가 말풍선으로 하나씩 전하고 (홈을 열 때마다 하나, 하루 3개까지), 일정은 노란 띠, 나머지는 소식함에 30일 남는다.
// 항목표: project-files notes/홈-리마인드-항목표-2026-10-11.md

/** Swift Calendar.current weekday (1 = 일요일 … 7 = 토요일) */
private fun weekday(at: Instant = Instant.now()): Int = at.atZone(ZoneId.systemDefault()).dayOfWeek.value % 7 + 1

// ios: Data/Remind.swift:8,9
@Serializable
enum class RemindCat {
    done, doing, event, market, analysis, news;
    // ios: Data/Remind.swift:10,11
    val label: String get() = when (this) { done -> "달성"; doing -> "진행 중"; event -> "일정"; market -> "증시 요약"; analysis -> "분석"; news -> "설정·새 소식" }
    // ios: Data/Remind.swift:16,17
    val icon: String get() = when (this) { done -> "sparkles"; doing -> "hourglass"; event -> "calendar"; market -> "newspaper"; analysis -> "chart.line.uptrend.xyaxis"; news -> "gift" }
}
// ios: Data/Remind.swift:13,14
val RemindCat.color: Color @Composable get() = when (this) { RemindCat.done -> Theme.green; RemindCat.doing -> Theme.purple; RemindCat.event -> Theme.orange; RemindCat.market -> Theme.blue; RemindCat.analysis -> Theme.blue; RemindCat.news -> Theme.teal }

/** 소식을 누르면 갈 곳 */
// ios: Data/Remind.swift:22,23
sealed class RemindGo {
    object nowhere : RemindGo(); data class web(val url: String) : RemindGo(); object shelter : RemindGo(); data class char(val id: String) : RemindGo(); object me : RemindGo(); data class reader(val i: Int) : RemindGo()
    object board : RemindGo(); object week : RemindGo(); object calm : RemindGo(); object myPath : RemindGo(); object forecast : RemindGo(); object rateScen : RemindGo(); object external : RemindGo()
    data class hold(val s: String) : RemindGo(); object support : RemindGo(); object widgets : RemindGo(); object sync : RemindGo(); object alerts : RemindGo(); object krSum : RemindGo()
    companion object {
        /** 사이트 소식 파일의 go 글자 ("shelter", "char:ir", "read:3", "hold:TSLA" …) */
        // ios: Data/Remind.swift:26
        fun of(s0: String?): RemindGo {
            val s = s0 ?: ""   // ios: Data/Remind.swift:27
            if (s.startsWith("char:")) return char(s.drop(5))   // ios: Data/Remind.swift:28
            if (s.startsWith("read:")) s.drop(5).toIntOrNull()?.let { return reader(it) }   // ios: Data/Remind.swift:29
            if (s.startsWith("hold:")) return hold(s.drop(5))   // ios: Data/Remind.swift:30
            // ios: Data/Remind.swift:31,32
            val m: Map<String, RemindGo> = mapOf("shelter" to shelter, "me" to me, "board" to board, "week" to week, "calm" to calm, "myPath" to myPath, "forecast" to forecast,
                                                 "rateScen" to rateScen, "external" to external, "support" to support, "widgets" to widgets, "sync" to sync, "alerts" to alerts)
            return m[s] ?: nowhere   // ios: Data/Remind.swift:33
        }
    }
}

// ios: Data/Remind.swift:37
data class RemindItem(
    val id: String,           // ios: Data/Remind.swift:38 같은 소식이면 같은 id (본 기록에 쓴다)
    val cat: RemindCat,       // ios: Data/Remind.swift:39
    val text: String,         // ios: Data/Remind.swift:40
    val go: RemindGo = RemindGo.nowhere,   // ios: Data/Remind.swift:41
    val once: Boolean = true,      // ios: Data/Remind.swift:42 한 번: 보면 지난 소식으로. 아니면 조건이 풀릴 때까지 남는다
    val big: Boolean = false,      // ios: Data/Remind.swift:43 위젯에도 보여 줄 큰 달성 (내 캐릭터 열림, 새 친구, 1000칸 이정표)
    val day: String? = null,       // ios: Data/Remind.swift:44 일정 날짜 (yyyy-MM-dd)
    val sub: String? = null,       // ios: Data/Remind.swift:45 일정의 대상 (전 종목, 환율, 종목 이름)
    val action: String? = null,    // ios: Data/Remind.swift:46 할 일 버튼 이름 (있으면 소식함 맨 위 '지금 해 주면 좋아요'에 올린다)
    val why: String? = null,       // ios: Data/Remind.swift:47 왜 중요한지 한 줄 (할 일·일정)
    val checks: List<Check>? = null,   // ios: Data/Remind.swift:48 체크리스트 (내 투자 캐릭터 찾기)
) {
    data class Check(val text: String, val ok: Boolean)   // ios: Data/Remind.swift:49
    /** 소식함 '할 일': 행동 버튼이 있거나 조건이 풀릴 때까지 남는 진행 중 소식 */
    val isTodo: Boolean get() = cat != RemindCat.event && (action != null || !once)   // ios: Data/Remind.swift:51
}

/** 기기에 남는 기록 (다른 기기와 맞추지 않는다) */
// ios: Data/Remind.swift:55
@Serializable
data class RemindLog(
    var since: Double = 0.0,                     // ios: Data/Remind.swift:56 기록을 시작한 때 (이보다 오래된 개발자 소식은 건너뛴다)
    var first: Map<String, Double> = emptyMap(),   // ios: Data/Remind.swift:57 소식이 처음 뜬 때 (30일 보관)
    var seen: Map<String, Double> = emptyMap(),    // ios: Data/Remind.swift:58 본 때
    var off: Set<RemindCat> = emptySet(),        // ios: Data/Remind.swift:59 설정에서 끈 분류
    var bubbleDay: String? = null,        // ios: Data/Remind.swift:60 말풍선 하루 3개 세기
    var bubbleN: Int = 0,                 // ios: Data/Remind.swift:61
    var bubble: String? = null,           // ios: Data/Remind.swift:62 지금 말풍선에 띄운 소식
    var tier: Int? = null,                // ios: Data/Remind.swift:63 예전 후원 단계 (지금은 안 씀, 저장 파일 호환)
    var cellsMax: Int = 0,                // ios: Data/Remind.swift:64 1000칸 최고 기록
    var cellsWeek: String? = null,        // ios: Data/Remind.swift:65 최고 기록을 잰 주와 그 주가 시작될 때의 최고 기록
    var cellsWeekMax: Int = 0,            // ios: Data/Remind.swift:66
    var weeks: Map<String, Wk> = emptyMap(),       // ios: Data/Remind.swift:67 주간 예보: 주(yyyy-Www)마다 처음 연 날의 범위와 마지막으로 본 평가액
    var snap: Map<String, List<Double>> = emptyMap(),  // ios: Data/Remind.swift:68 분석: 주마다 [목표(본전) 가능성, 내 길 앞섬]
    var analysisWeek: String? = null,     // ios: Data/Remind.swift:69 분석 소식이 마지막으로 뜬 주 (주 1회까지)
    var guessHits: Int? = null,           // ios: Data/Remind.swift:70 맞혀보기를 맞힌 횟수 (배지). 옛 기록에 없는 키라 옵셔널
) {
    // ios: Data/Remind.swift:71
    @Serializable
    data class Wk(
        var lo: Double, var hi: Double, var last: Double,   // ios: Data/Remind.swift:72
        var guess: String? = null,        // ios: Data/Remind.swift:73 그 주에 고른 답 (lo·in·hi)
        var scored: Boolean? = null,      // ios: Data/Remind.swift:74 맞힌 횟수에 이미 셌는지
    ) {
        /** 금요일 값이 범위의 어디서 끝났는지 (lo·in·hi) */
        val pos: String get() = if (last < lo) "lo" else if (last > hi) "hi" else "in"   // ios: Data/Remind.swift:76
    }
}

/** 사이트 미래 설계 글 묶음 data/blog.json (제목·날짜와 본문 첫 문장만 쓴다) */
// ios: Data/Remind.swift:81
@Serializable
data class BlogFile(
    val cats: List<Cat> = emptyList(),   // ios: Data/Remind.swift:94
    val posts: List<Post> = emptyList(),   // ios: Data/Remind.swift:95
) {
    @Serializable data class Cat(val id: String, val name: String)   // ios: Data/Remind.swift:82
    // ios: Data/Remind.swift:83
    @Serializable data class Post(
        val id: String, val title: String, val cat: String, val created: String? = null, val body: String? = null,   // ios: Data/Remind.swift:84
    ) {
        /** 본문 첫 문장 (빈 줄·제목 기호를 건너뛰고 첫 . ? ! 까지) */
        // ios: Data/Remind.swift:86
        val lead: String get() {
            val line = (body ?: "").split("\n").map { it.trim { c -> "#>*- ".contains(c) || c.isWhitespace() } }.firstOrNull { it.isNotEmpty() } ?: ""   // ios: Data/Remind.swift:87
            // 3.5% 같은 숫자에서 끊지 않게, 뒤가 띄어쓰기이거나 줄 끝인 . ? ! 에서 끊는다
            val cs = line.toCharArray()   // ios: Data/Remind.swift:89
            val e = cs.indices.firstOrNull { ".?!".contains(cs[it]) && (it + 1 == cs.size || cs[it + 1].toString() == " ") } ?: return line   // ios: Data/Remind.swift:90
            return String(cs, 0, e + 1)   // ios: Data/Remind.swift:91
        }
    }
}

/** 개발자 소식: 앱 버전마다 적어 두는 목록 + 사이트 data/app_news.json (앱 업데이트 없이 올라온 글·일정) */
// ios: Data/Remind.swift:99
@Serializable
data class RemindNews(
    val id: String,   // ios: Data/Remind.swift:100
    val date: String,   // ios: Data/Remind.swift:101
    val text: String,   // ios: Data/Remind.swift:102
    val go: String? = null,   // ios: Data/Remind.swift:103
) {
    companion object {
        // ios: Data/Remind.swift:105,106,107,108
        val app: List<RemindNews> = listOf(
            RemindNews(id = "n1011.remind", date = "2026-10-11", text = "홈에 리마인드가 생겼어요. 소식은 친구가 전하고, 지난 소식은 ‘새 소식’에 모여요."),
            RemindNews(id = "n1011.edit", date = "2026-10-11", text = "종목 목록에서 연필을 누르면 바로 수량·단가를 고칠 수 있어요.", go = "hold:"),
            RemindNews(id = "n1011.items", date = "2026-10-11", text = "쉼터 물건을 누르면 배치에서 빼고 다시 놓을 수 있어요.", go = "shelter"),
        )
    }
}

// ios: Data/Remind.swift:112,113
class Remind private constructor() {
    // ios: Data/Remind.swift:119 (상태라 바뀌면 화면이 다시 그려진다. 바뀌면 저장)
    var log: RemindLog by mutableStateOf(load())
        private set
    private fun setLog(f: (RemindLog) -> RemindLog) { val n = f(log); if (n != log) { log = n; save() } }
    /** 지금 소식 (refresh 가 채운다. 화면은 이 값만 읽는다) */
    var items: List<RemindItem> by mutableStateOf(emptyList())   // ios: Data/Remind.swift:121
        private set
    var site: List<RemindNews> by mutableStateOf(emptyList())   // ios: Data/Remind.swift:122
        private set
    var blog: BlogFile by mutableStateOf(BlogFile())   // ios: Data/Remind.swift:123
        private set
    private var siteAt = 0L   // ios: Data/Remind.swift:127

    companion object {
        val shared: Remind by lazy { Remind() }   // ios: Data/Remind.swift:114
        private const val key = "remind.v1"   // ios: Data/Remind.swift:115
        private const val keep = 30 * 86400.0   // ios: Data/Remind.swift:116
        private const val bubbleMax = 3   // ios: Data/Remind.swift:117
        /** 소식함에 올리는 미래 설계 카테고리와 앱에 남기는 날 수 */
        const val blogCat = "2026 지구"   // ios: Data/Remind.swift:125
        private const val blogDays = 10.0   // ios: Data/Remind.swift:126

        /** 맞혀보기 배지: 맞힌 횟수가 이만큼 되면 받는다 */
        class Badge(val n: Int, val name: String, val icon: String)
        // ios: Data/Remind.swift:305,306,307
        val badges: List<Badge> = listOf(
            Badge(1, "첫 적중", "star.fill"), Badge(3, "세 번 적중", "sparkles"), Badge(5, "다섯 번 적중", "medal.fill"),
            Badge(10, "열 번 적중", "trophy.fill"), Badge(20, "예보 달인", "crown.fill"),
        )

        /** 끝난 주: 지난주들, 또는 토·일요일의 이번 주 (오래된 순) */
        // ios: Data/Remind.swift:311
        fun endedWeeks(w: Map<String, RemindLog.Wk>, now: Instant = Instant.now()): List<String> {
            val wk = week(now); val wd = weekday(now)   // ios: Data/Remind.swift:312
            return w.keys.filter { it < wk || (it == wk && (wd == 7 || wd == 1)) }.sorted()   // ios: Data/Remind.swift:313
        }

        /** 주 이름 (yyyy-Www, 월요일 시작) */
        // ios: Data/Remind.swift:501
        fun week(d: Instant): String {
            val c = d.atZone(ZoneId.systemDefault()).toLocalDate(); val y = c.get(IsoFields.WEEK_BASED_YEAR); val w = c.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)   // ios: Data/Remind.swift:502
            return cformat("%04d-W%02d", y, w)   // ios: Data/Remind.swift:503
        }

        private fun load(): RemindLog =   // ios: Data/Remind.swift:129,130
            Defaults.prefs.getString(key, null)?.let { runCatching { NJson.decodeFromString(RemindLog.serializer(), it) }.getOrNull() } ?: RemindLog()
    }

    init {
        // 받아 둔 사이트 소식·글 묶음 (작은 파일, 처음 한 번)
        Store.readData("app_news.json")?.let { d -> site = runCatching { NJson.decodeFromString(ListSerializer(RemindNews.serializer()), d.decodeToString()) }.getOrDefault(emptyList()) }   // ios: Data/Remind.swift:131
        Store.readData("blog.json")?.let { d -> runCatching { NJson.decodeFromString(BlogFile.serializer(), d.decodeToString()) }.getOrNull()?.let { blog = it } }   // ios: Data/Remind.swift:132
    }
    private fun save() { Defaults.prefs.edit().putString(key, NJson.encodeToString(RemindLog.serializer(), log)).apply() }   // ios: Data/Remind.swift:134

    // MARK: 보여 줄 것

    fun isOn(c: RemindCat): Boolean = !log.off.contains(c)   // ios: Data/Remind.swift:138
    fun setOn(c: RemindCat, on: Boolean) { setLog { it.copy(off = if (on) it.off - c else it.off + c) } }   // ios: Data/Remind.swift:139
    private val shown: List<RemindItem> get() = items.filter { isOn(it.cat) }   // ios: Data/Remind.swift:140
    fun isSeen(i: RemindItem): Boolean = log.seen[i.id] != null   // ios: Data/Remind.swift:141
    /** 안 본 소식 (말풍선·‘새 소식 n’) */
    val fresh: List<RemindItem> get() = shown.filter { it.once && it.cat != RemindCat.event && !isSeen(it) }   // ios: Data/Remind.swift:143
    val doing: List<RemindItem> get() = shown.filter { !it.once && it.cat != RemindCat.event }   // ios: Data/Remind.swift:144
    // ios: Data/Remind.swift:145
    val past: List<RemindItem> get() =
        shown.filter { it.once && it.cat != RemindCat.event && isSeen(it) }.sortedWith { x, y -> (log.first[y.id] ?: 0.0).compareTo(log.first[x.id] ?: 0.0) }   // ios: Data/Remind.swift:146
    /** 이번 주 일정 (오늘부터 7일) */
    val events: List<RemindItem> get() = shown.filter { it.cat == RemindCat.event }   // ios: Data/Remind.swift:149
    /** 홈 노란 띠: 오늘부터 3일 안의 일정 (내 종목 먼저) */
    // ios: Data/Remind.swift:151
    val strip: RemindItem? get() {
        val until = Day.str(Instant.now().plusSeconds(3 * 86400))   // ios: Data/Remind.swift:152
        return events.firstOrNull { (it.day ?: "") <= until }   // ios: Data/Remind.swift:153
    }
    /** 홈 말풍선: 지금 띄운 소식 */
    val bubble: RemindItem? get() = log.bubble?.let { id -> items.firstOrNull { it.id == id } }?.let { if (isOn(it.cat)) it else null }   // ios: Data/Remind.swift:156

    /** 홈을 열 때: 다음 소식을 말풍선에 (하루 3개까지). 띄운 소식은 본 것으로 친다 */
    // ios: Data/Remind.swift:159
    fun nextBubble() {
        val today = Day.today   // ios: Data/Remind.swift:160
        var l = log
        if (l.bubbleDay != today) l = l.copy(bubbleDay = today, bubbleN = 0)   // ios: Data/Remind.swift:161
        if (l.bubbleN >= bubbleMax) { setLog { l.copy(bubble = null) }; return }   // ios: Data/Remind.swift:162
        val order = listOf(RemindCat.done, RemindCat.news, RemindCat.analysis)   // ios: Data/Remind.swift:163
        val new = fresh   // ios: Data/Remind.swift:164
        val pick = order.asSequence().mapNotNull { c -> new.firstOrNull { it.cat == c } }.firstOrNull()   // ios: Data/Remind.swift:165
        // 서재: 수·일요일에만 (주 1~2회)
        val wd = weekday()   // ios: Data/Remind.swift:167
        // ios: Data/Remind.swift:168,169,170
        val r = doing.firstOrNull { it.id.startsWith("read.") }
        if (pick == null && (wd == 4 || wd == 1) && r != null && l.seen[r.id + "." + today] == null) {
            val now = Instant.now().toEpochMilli() / 1000.0
            setLog { l.copy(seen = l.seen + (r.id + "." + today to now), bubble = r.id, bubbleN = l.bubbleN + 1) }; return
        }
        if (pick == null) { setLog { l.copy(bubble = null) }; return }   // ios: Data/Remind.swift:172
        // ios: Data/Remind.swift:173,174
        setLog { l.copy(bubble = pick.id, bubbleN = l.bubbleN + 1, seen = l.seen + (pick.id to Instant.now().toEpochMilli() / 1000.0)) }
    }
    // ios: Data/Remind.swift:176
    fun markAllSeen() {
        val t = Instant.now().toEpochMilli() / 1000.0   // ios: Data/Remind.swift:177
        val add = fresh.associate { it.id to t }   // ios: Data/Remind.swift:178
        if (add.isNotEmpty()) setLog { it.copy(seen = it.seen + add) }
    }

    /** 위젯 말풍선: 오늘 처음 뜬 큰 달성 하나 */
    // ios: Data/Remind.swift:182
    val widgetNews: String? get() {
        val today = Day.today   // ios: Data/Remind.swift:183
        return items.firstOrNull { it.big && log.first[it.id]?.let { t -> Day.str(Instant.ofEpochMilli((t * 1000).toLong())) == today } == true && isOn(it.cat) }?.text   // ios: Data/Remind.swift:184
    }
    /** 인물 · 블록 위젯 표시: 오늘 넘은 1000칸 이정표 */
    // ios: Data/Remind.swift:187
    val widgetBadge: String? get() {
        val today = Day.today   // ios: Data/Remind.swift:188
        val i = items.firstOrNull { it.id.startsWith("cells.m") } ?: return null; val t = log.first[i.id] ?: return null   // ios: Data/Remind.swift:189
        if (Day.str(Instant.ofEpochMilli((t * 1000).toLong())) != today) return null
        return "★ ${i.id.drop(7)}칸 돌파"   // ios: Data/Remind.swift:190
    }

    // MARK: 만들기

    /** 지금 상태로 소식을 다시 만든다 (홈을 열 때, 1분 갱신 때) */
    // ios: Data/Remind.swift:196
    fun refresh(m: AppModel) {
        val now = Instant.now().toEpochMilli() / 1000.0   // ios: Data/Remind.swift:197
        if (log.since == 0.0) setLog { it.copy(since = now) }   // ios: Data/Remind.swift:198
        var out = ArrayList<RemindItem>()   // ios: Data/Remind.swift:199
        out += achievements(m)   // ios: Data/Remind.swift:200
        out += progress(m)   // ios: Data/Remind.swift:201
        // ios: Data/Remind.swift:202,203
        out += MarketEvents.upcoming(held = m.rows.map { it.id }, days = 7).map { e ->
            RemindItem(id = "ev.${e.id}.${e.day}", cat = RemindCat.event, text = e.kind, go = RemindGo.external, once = false, day = e.day, sub = e.sub, why = MarketEvents.why(e.id))
        }
        out += analysis(m)   // ios: Data/Remind.swift:205
        out += news()   // ios: Data/Remind.swift:206
        // 한 번 소식: 처음 뜬 때를 적고, 30일 지나면 뺀다
        // (지금도 조건이 맞는 소식의 기록은 지우지 않는다. 지우면 다시 새 소식으로 뜬다)
        var first = log.first.toMutableMap()   // ios: Data/Remind.swift:209
        for (i in out) if (i.once && first[i.id] == null) first[i.id] = now   // ios: Data/Remind.swift:210
        val live = out.map { it.id }.toSet()   // ios: Data/Remind.swift:211
        out = ArrayList(out.filter { !it.once || now - (first[it.id] ?: now) < keep })   // ios: Data/Remind.swift:212
        first = first.filter { live.contains(it.key) || now - it.value < keep * 2 }.toMutableMap()   // ios: Data/Remind.swift:213
        if (first != log.first) setLog { it.copy(first = first) }   // ios: Data/Remind.swift:214
        val seen = log.seen.filter { first[it.key] != null || now - it.value < keep * 2 }   // ios: Data/Remind.swift:215
        if (seen.size != log.seen.size) setLog { it.copy(seen = seen) }   // ios: Data/Remind.swift:216
        if (out != items) items = out   // ios: Data/Remind.swift:217
        MainScope().launch { loadSite() }   // ios: Data/Remind.swift:218
    }

    /** 앱 단계·달성 (같은 종류는 가장 최근 단계 하나만) */
    // ios: Data/Remind.swift:222
    private fun achievements(m: AppModel): List<RemindItem> {
        val a = ArrayList<RemindItem>()   // ios: Data/Remind.swift:223
        if (m.playOn) a.add(RemindItem(id = "start", cat = RemindCat.done, text = "앱 시작 3단계를 마쳤어요."))   // ios: Data/Remind.swift:224
        // 인터미션
        // ios: Data/Remind.swift:226
        if (m.interDone) {
            a.add(RemindItem(id = "inter.done.${m.route.name}", cat = RemindCat.done, text = "인터미션을 다 마쳤어요. 친구가 모두 모였어요.", go = RemindGo.shelter))   // ios: Data/Remind.swift:227
        } else if (m.route == Route.recover && m.weeks.size in 1..3) {   // ios: Data/Remind.swift:228
            val n = m.weeks.size   // ios: Data/Remind.swift:229
            a.add(RemindItem(id = "inter.r.$n", cat = RemindCat.done, text = "인터미션 $n/4주 끝! 다음 주는 ‘${Shelter.weekSteps[n].task}’예요.", go = RemindGo.board))   // Data/Remind.swift:230 (검사 예외: 같은 글)
        } else if (m.isGoal && m.gWeeks.size in 1..2) {   // ios: Data/Remind.swift:231
            val n = m.gWeeks.size   // ios: Data/Remind.swift:232
            a.add(RemindItem(id = "inter.g.$n", cat = RemindCat.done, text = "인터미션 $n/3달 끝! 다음 달은 ‘${AppModel.goalMonthSteps[n].task}’예요.", go = RemindGo.board))   // Data/Remind.swift:233 (검사 예외: 같은 글)
        }
        // 새 친구와 그 인물 위젯
        val k = m.friendsOpen   // ios: Data/Remind.swift:236
        // ios: Data/Remind.swift:237
        if (k >= 1 && !m.devAll) {
            val f = Shelter.friends[min(k, Shelter.friends.size - 1)]   // ios: Data/Remind.swift:238
            a.add(RemindItem(id = "friend.$k", cat = RemindCat.done, text = "${f.name}가 쉼터에 왔어요. 인사해 볼래요?", go = RemindGo.char(f.id), big = true))   // Data/Remind.swift:239 (검사 예외: 같은 글)
            a.add(RemindItem(id = "widget.$k", cat = RemindCat.done, text = "${f.name} 인물 위젯을 홈 화면에 둘 수 있어요.", go = RemindGo.widgets))   // Data/Remind.swift:240 (검사 예외: 같은 글)
        }
        // 돌아온 물건
        val n = m.itemsOn   // ios: Data/Remind.swift:243
        // ios: Data/Remind.swift:244
        if (n >= 1 && !m.devAll) {
            a.add(RemindItem(id = "items.$n", cat = RemindCat.done, text = "물건이 하나 돌아왔어요: ${Shelter.items[n - 1].name} ($n/10).", go = RemindGo.shelter))   // Data/Remind.swift:245 (검사 예외: 같은 글)
        }
        // 평정 지수
        val c = m.calm   // ios: Data/Remind.swift:248
        // ios: Data/Remind.swift:249
        if (c.events.size >= Calm.minEvents) {
            a.add(RemindItem(id = "calm.first", cat = RemindCat.done, text = "기록 ${Calm.minEvents}건을 채워 평정 지수가 처음 나왔어요: ${c.stats().index}점.", go = RemindGo.calm))   // Data/Remind.swift:250 (검사 예외: 같은 글)
        }
        val t = c.type; val kd = Calm.kind(t)
        // ios: Data/Remind.swift:252
        if (t != null && kd != null) {
            val who = m.friendName(t)   // ios: Data/Remind.swift:253
            // Data/Remind.swift:254 (검사 예외: 같은 글)
            val text = if (c.prevType == null) "투자 유형이 정해졌어요: ‘${kd.title}’. ${who}와 닮았어요." else "이번 달은 ‘${kd.title}’에 더 가까워요. ${who}와 함께해요."
            a.add(RemindItem(id = "calm.type.$t.${c.typeMonth ?: ""}", cat = RemindCat.done, text = text, go = RemindGo.calm))   // Data/Remind.swift:255 (검사 예외: 빈 글 "" 이 든 보간)
            // ios: Data/Remind.swift:256
            if (c.stage >= 2) {
                val d = if (c.stage == 2) 14 else 60   // ios: Data/Remind.swift:257
                a.add(RemindItem(id = "calm.stage.$t.${c.stage}", cat = RemindCat.done, text = "${who}와 함께한 지 ${d}일. 말투가 조금 바뀔 거예요.", go = RemindGo.char("$t:invest")))   // Data/Remind.swift:258 (검사 예외: 같은 글)
            }
        }
        // ios: Data/Remind.swift:261
        calmChange(c)?.let { ch ->
            a.add(RemindItem(id = "calm.chg.${ch.first}", cat = RemindCat.done, text = "평정 지수가 지난달 ${ch.second}점에서 ${ch.third}점으로 ${if (ch.third > ch.second) "올랐어요" else "내렸어요"}.", go = RemindGo.calm))   // Data/Remind.swift:262 (검사 예외: 같은 글)
        }
        // 다음 걸음: 습관 사다리에서 마친 걸음 (최근 3개), 세리가 지킨 반년
        // ios: Data/Remind.swift:265
        c.ladder?.let { l ->
            val who = m.voice(m.calmSpeaker)   // ios: Data/Remind.swift:266
            // ios: Data/Remind.swift:267
            for (d in l.done.takeLast(3)) {
                var text = Calm.stepLine(d.type, d.step, who = who)   // ios: Data/Remind.swift:268
                // ios: Data/Remind.swift:269
                val nx = Calm.steps[l.type]?.getOrNull(l.step - 1)
                if (d == l.done.lastOrNull() && d.type == l.type && l.step in 1..3 && nx != null) {
                    text += " 다음 걸음은 ‘${nx.name}’."   // Data/Remind.swift:270 (검사 예외: 같은 글)
                }
                a.add(RemindItem(id = "calm.step.${d.type}.${d.step}.${d.day}", cat = RemindCat.done, text = text, go = RemindGo.calm))   // ios: Data/Remind.swift:272
            }
            // ios: Data/Remind.swift:274
            if (l.keeps > 0) {
                a.add(RemindItem(id = "calm.keep.${l.keeps}", cat = RemindCat.done, text = "평정 지수 80 이상을 ${6 * (l.keeps + 1)}달 지켰어요.", go = RemindGo.calm))   // Data/Remind.swift:275 (검사 예외: 같은 글)
            }
        }
        // 쉼터 배경: 하루 2곳씩 열린다 (2026-10-16 결정)
        val bg = m.bgOpen   // ios: Data/Remind.swift:279
        if (bg > 0) a.add(RemindItem(id = "bg.open.$bg", cat = RemindCat.done, text = "쉼터 배경 ${bg}곳이 열렸어요 ($bg/${Shelter.backgrounds.size}). 골라 봐요.", go = RemindGo.shelter))   // Data/Remind.swift:280 (검사 예외: 같은 글)
        a += cells(m)   // ios: Data/Remind.swift:281
        a += week(m)   // ios: Data/Remind.swift:282
        return a   // ios: Data/Remind.swift:283
    }

    /** 1000칸 이정표·최고 기록 */
    // ios: Data/Remind.swift:287
    private fun cells(m: AppModel): List<RemindItem> {
        if (!(m.playUnlocked && m.keyValue > 0 && m.rows.isNotEmpty())) return emptyList()   // ios: Data/Remind.swift:288
        val c = (max(0.0, min(1.0, m.trackValue / m.keyValue)) * 1000).toInt()   // ios: Data/Remind.swift:289
        val wk = week(Instant.now())   // ios: Data/Remind.swift:290
        if (log.cellsWeek != wk) setLog { it.copy(cellsWeek = wk, cellsWeekMax = it.cellsMax) }   // ios: Data/Remind.swift:291
        if (c > log.cellsMax) setLog { it.copy(cellsMax = c) }   // ios: Data/Remind.swift:292
        val a = ArrayList<RemindItem>()   // ios: Data/Remind.swift:293
        // ios: Data/Remind.swift:294
        listOf(1000, 750, 500, 250, 100).firstOrNull { log.cellsMax >= it }?.let { ms ->
            // Data/Remind.swift:295 (검사 예외: 같은 글)
            val text = if (ms == 1000) "1000칸을 다 채웠어요!" else "1000칸 중 ${ms}칸을 넘었어요." + (if (ms == 250) " 4분의 1이에요!" else if (ms == 500) " 절반이에요!" else "")
            a.add(RemindItem(id = "cells.m$ms", cat = RemindCat.done, text = text, go = RemindGo.board, big = true))   // ios: Data/Remind.swift:296
        }
        // ios: Data/Remind.swift:298
        if (log.cellsWeekMax > 0 && log.cellsMax > log.cellsWeekMax) {
            a.add(RemindItem(id = "cells.best.$wk", cat = RemindCat.done, text = "1000칸 최고 기록 ${log.cellsMax}칸을 새로 썼어요.", go = RemindGo.board))   // Data/Remind.swift:299 (검사 예외: 같은 글)
        }
        return a   // ios: Data/Remind.swift:301
    }

    /** 주간 예보 기록: 그 주 처음 연 날(월~금)의 범위를 적어 두고, 고른 답과 마지막 평가액을 이어 적는다.
     *  끝난 주의 고른 답은 한 번만 맞힌 횟수에 센다. 홈 리마인드와 미션 탭 예보 카드가 함께 부른다. */
    // ios: Data/Remind.swift:318
    fun recordWeek(m: AppModel) {
        if (!m.playUnlocked || m.rows.isEmpty()) return   // ios: Data/Remind.swift:319
        val now = Instant.now(); val wk = week(now); val wd = weekday(now)   // ios: Data/Remind.swift:320
        val w = log.weeks.toMutableMap()   // ios: Data/Remind.swift:321
        // ios: Data/Remind.swift:322
        if (w[wk] == null && wd in 2..6) {
            val r = m.weekRange   // ios: Data/Remind.swift:323
            if (m.weekGuess != null) m.weekGuess = null   // ios: Data/Remind.swift:324 새 주: 지난주에 고른 답은 지난주 기록에만 남는다
            w[wk] = RemindLog.Wk(lo = r.lo, hi = r.hi, last = m.trackValue)   // ios: Data/Remind.swift:325
        } else if (w[wk] != null) {   // ios: Data/Remind.swift:326
            w[wk] = w[wk]!!.copy(last = m.trackValue)   // ios: Data/Remind.swift:327
            if (wd in 2..6) w[wk] = w[wk]!!.copy(guess = m.weekGuess)   // ios: Data/Remind.swift:328 금요일 마감 전까지만 고칠 수 있다
        }
        var hits = log.guessHits ?: 0   // ios: Data/Remind.swift:330
        // ios: Data/Remind.swift:331
        for (k in endedWeeks(w, now = now)) {
            val x = w[k] ?: continue; val g = x.guess ?: continue; if (x.scored == true) continue   // ios: Data/Remind.swift:332
            w[k] = x.copy(scored = true)   // ios: Data/Remind.swift:333
            if (g == x.pos) hits += 1   // ios: Data/Remind.swift:334
        }
        if (w.size > 12) for (k in w.keys.sorted().take(w.size - 12)) w.remove(k)   // ios: Data/Remind.swift:336
        if (hits != (log.guessHits ?: 0)) setLog { it.copy(guessHits = hits) }   // ios: Data/Remind.swift:337
        // ios: Data/Remind.swift:338,339
        if (w.size != log.weeks.size || w[wk]?.last != log.weeks[wk]?.last || w[wk]?.guess != log.weeks[wk]?.guess
            || w.any { it.value.scored != log.weeks[it.key]?.scored }) setLog { it.copy(weeks = w) }
    }

    /** 주간 예보 결과: 그 주 처음 연 날의 범위와 금요일 뒤 평가액을 견준다 */
    // ios: Data/Remind.swift:343
    private fun week(m: AppModel): List<RemindItem> {
        if (!m.playUnlocked || m.rows.isEmpty()) return emptyList()   // ios: Data/Remind.swift:344
        recordWeek(m)   // ios: Data/Remind.swift:345
        val w = log.weeks; val ended = endedWeeks(w)   // ios: Data/Remind.swift:346
        val a = ArrayList<RemindItem>()   // ios: Data/Remind.swift:347
        // ios: Data/Remind.swift:348
        badges.lastOrNull { it.n <= (log.guessHits ?: 0) }?.let { b ->
            a.add(RemindItem(id = "badge.${b.n}", cat = RemindCat.done, text = "맞혀보기 배지 ‘${b.name}’를 받았어요. 예보를 ${b.n}번 맞혔어요.", go = RemindGo.week))   // Data/Remind.swift:349 (검사 예외: 같은 글)
        }
        val last = ended.lastOrNull() ?: return a; val r = w[last] ?: return a   // ios: Data/Remind.swift:351
        val inside = r.last >= r.lo && r.last <= r.hi   // ios: Data/Remind.swift:352
        var run = 0   // ios: Data/Remind.swift:353
        for (k in ended.reversed()) { val x = w[k]; if (x == null || !(x.last >= x.lo && x.last <= x.hi)) break; run += 1 }   // ios: Data/Remind.swift:354
        // Data/Remind.swift:355,356 (검사 예외: 같은 글)
        val text = if (inside) "이번 주 예보 적중! 범위 안에서 마쳤어요." + (if (run >= 2) " ${run}주 연속이에요." else "")
            else "이번 주는 예보 범위 ${if (r.last > r.hi) "위" else "아래"}로 나갔어요. 이런 주도 절반쯤 있어요."
        return a + listOf(RemindItem(id = "wk.$last", cat = RemindCat.done, text = text, go = RemindGo.week))   // ios: Data/Remind.swift:357
    }

    /** 평정 지수 달 변화: 이번 달 전까지의 기록과 지금을 견준다 (양쪽 10건 이상, 5점 넘게 바뀌었을 때) */
    // ios: Data/Remind.swift:361
    private fun calmChange(c: Calm): Triple<String, Int, Int>? {
        val month = Day.today.take(7)   // ios: Data/Remind.swift:362
        val before = c.events.filter { it.day < month + "-01" }   // ios: Data/Remind.swift:363
        if (!(before.size >= Calm.minEvents && before.size < c.events.size)) return null   // ios: Data/Remind.swift:364
        val a = swRound(Calm.index(before)); val b = swRound(Calm.index(c.events))   // ios: Data/Remind.swift:365
        return if (abs(b - a) >= 5) Triple(month, a, b) else null   // ios: Data/Remind.swift:366
    }
    /** Swift .rounded() (0.5 는 0 에서 먼 쪽) */
    private fun swRound(x: Double): Int = if (x < 0) -kotlin.math.floor(-x + 0.5).toInt() else kotlin.math.floor(x + 0.5).toInt()

    /** 진행 중 (조건이 풀릴 때까지 소식함에 남는다) */
    // ios: Data/Remind.swift:370
    private fun progress(m: AppModel): List<RemindItem> {
        val a = ArrayList<RemindItem>()   // ios: Data/Remind.swift:371
        // ios: Data/Remind.swift:372
        if (m.playOn && !m.interDone) {
            // Data/Remind.swift:374,375 (검사 예외: 같은 글)
            var t = if (m.isGoal) "인터미션 ${m.gWeeks.size}/3달 · 이번 달: ${AppModel.goalMonthSteps[min(m.gWeeks.size, 2)].task}"
                else "인터미션 ${m.weeks.size}/4주 · 이번 주: ${Shelter.weekSteps[min(m.weeks.size, 3)].task}"   // ios: Data/Remind.swift:373 (t = isGoal ? … : …)
            val g = m.weekGate
            if (!m.isGoal && g is WeekGate.wait) t += " · ${m.weeks.size + 1}주차는 ${g.days}일 뒤 열려요"   // Data/Remind.swift:376 (검사 예외: 같은 글)
            a.add(RemindItem(id = "do.inter", cat = RemindCat.doing, text = t, go = RemindGo.board, once = false))   // ios: Data/Remind.swift:377
        }
        val n = m.calm.events.size   // ios: Data/Remind.swift:379
        val calmLeft = if (n in 1 until Calm.minEvents) Calm.minEvents - n else null   // ios: Data/Remind.swift:380
        // ios: Data/Remind.swift:381,382
        val l = m.calm.ladder; val st = l?.let { Calm.steps[it.type]?.getOrNull(it.step - 1) }; val p = m.calm.progress()
        if (l != null && l.step in 1..3 && st != null && p != null) {
            a.add(RemindItem(id = "do.ladder", cat = RemindCat.doing, text = "다음 걸음 ${l.step}/3 ‘${st.name}’ · ${p.text}", go = RemindGo.calm, once = false))   // Data/Remind.swift:382 (검사 예외: 같은 글)
        }
        // ios: Data/Remind.swift:384
        calmLeft?.let { c ->
            a.add(RemindItem(id = "do.calm", cat = RemindCat.doing, text = "내 투자 습관까지 매매 기록 ${c}건 남았어요.", go = RemindGo.calm, once = false))   // Data/Remind.swift:385 (검사 예외: 같은 글)
        }
        // ios: Data/Remind.swift:387
        (0 until Shelter.friends.size).firstOrNull { !m.friendOn(it) }?.let { i ->
            a.add(RemindItem(id = "do.friend", cat = RemindCat.doing, text = "다음 친구 ${Shelter.friends[i].name}는 ${m.friendWhen(i)}에 와요.", go = RemindGo.shelter, once = false))   // Data/Remind.swift:388 (검사 예외: 같은 글)
        }
        // ios: Data/Remind.swift:390
        if (m.playUnlocked && m.weekGuess == null) {
            a.add(RemindItem(id = "do.week", cat = RemindCat.doing, text = "이번 주 금요일 평가액은 어디쯤일까요? 예보를 골라 봐요.", go = RemindGo.week, once = false))   // ios: Data/Remind.swift:391
        }
        // 서재: 열렸지만 다 안 읽은 장 하나
        val story = Story.shared   // ios: Data/Remind.swift:394
        // ios: Data/Remind.swift:395
        (0 until Shelter.chapters.size).firstOrNull { m.chapterOn(it) && !story.isDone(StoryBook.side, it) }?.let { i ->
            val ch = Shelter.chapters[i]; val started = m.readPos.contains(i)   // ios: Data/Remind.swift:396
            // Data/Remind.swift:400 (검사 예외: 같은 글)
            a.add(RemindItem(id = "read.$i", cat = RemindCat.doing, text = "서재에 외전 ${ch.title} ‘${ch.name}’이 기다리고 있어요." + (if (started) " 이어 읽을래요?" else ""),
                             go = RemindGo.reader(i), once = false))   // ios: Data/Remind.swift:398
        }
        return a   // ios: Data/Remind.swift:400
    }

    /** 분석 소식 (내 종목 기준, 주 1회까지). 숫자는 매일 흔들려서 주마다 한 번 견준다 */
    // ios: Data/Remind.swift:404
    private fun analysis(m: AppModel): List<RemindItem> {
        if (m.rows.isEmpty()) return emptyList()   // ios: Data/Remind.swift:405
        val wk = week(Instant.now())   // ios: Data/Remind.swift:406
        val prob = if (m.isGoal) m.forecast.prob(m.gY.toDouble()) else m.planBreakEven(1.0)   // ios: Data/Remind.swift:407 years
        val gap = m.myPathGapToday   // ios: Data/Remind.swift:408
        val snap = log.snap.toMutableMap()   // ios: Data/Remind.swift:409
        if (snap[wk] == null) snap[wk] = listOf(prob, gap)   // ios: Data/Remind.swift:410
        if (snap.size > 10) for (k in snap.keys.sorted().take(snap.size - 10)) snap.remove(k)   // ios: Data/Remind.swift:411
        if (snap.size != log.snap.size) setLog { it.copy(snap = snap) }   // ios: Data/Remind.swift:412
        val tail = " 종목 추천이 아닙니다."   // ios: Data/Remind.swift:413
        val cand = ArrayList<RemindItem>()   // ios: Data/Remind.swift:414
        val pk = snap.keys.filter { it < wk }.maxOrNull(); val p = pk?.let { snap[it] }
        // ios: Data/Remind.swift:415
        if (p != null && p.size == 2) {
            // ios: Data/Remind.swift:416
            if (abs(prob - p[0]) >= 0.05) {
                val what = if (m.isGoal) "목표에 닿을 가능성" else "1년 안 본전 가능성"   // ios: Data/Remind.swift:417
                cand.add(RemindItem(id = "an.prob.$wk", cat = RemindCat.analysis, text = "${what}이 ${AppModel.pct(p[0])}에서 ${AppModel.pct(prob)}로 ${if (prob > p[0]) "올랐어요" else "내렸어요"}." + tail, go = if (m.isGoal) RemindGo.forecast else RemindGo.myPath))   // Data/Remind.swift:421 (검사 예외: 같은 글)
            }
            // ios: Data/Remind.swift:420
            if ((p[1] >= 0) != (gap >= 0)) {
                cand.add(RemindItem(id = "an.gap.$wk", cat = RemindCat.analysis, text = "내 길: 기준선보다 ${cformat("%.1f", abs(gap) * 100)}% ${if (gap >= 0) "앞서" else "뒤처져"} 있어요. 지난주와 반대쪽이에요." + tail, go = RemindGo.myPath))   // Data/Remind.swift:424 (검사 예외: 같은 글)
            }
        }
        val tot = max(1.0, m.total)   // ios: Data/Remind.swift:424
        val top = m.rows.maxByOrNull { it.value }
        // ios: Data/Remind.swift:425,426,427
        if (m.rows.size >= 2 && top != null && top.value / tot >= 0.5) {
            cand.add(RemindItem(id = "an.conc.${top.id}.${Day.today.take(7)}", cat = RemindCat.analysis,
                                text = "${top.sym.short} 비중이 ${AppModel.pct(top.value / tot)}예요. 한쪽으로 기울었어요." + tail, go = RemindGo.forecast))
        }
        val nw = MacroData.shared.file?.now; val d = nw?.date; val ch = nw?.change_1y
        // ios: Data/Remind.swift:429
        if (d != null && ch != null && abs(ch) >= 0.5) {
            val s = (if (ch > 0) "+" else "") + cformat("%.1f", ch)   // ios: Data/Remind.swift:430
            cand.add(RemindItem(id = "an.rate.${d.take(7)}", cat = RemindCat.analysis,   // ios: Data/Remind.swift:431
                                text = "미국 10년 금리가 1년 새 ${s}%p 움직였어요. 지난 자료로는 2년 뒤 이익에 ${if (ch > 0) "부담" else "도움"} 쪽이 많았어요." + tail, go = RemindGo.rateScen))   // Data/Remind.swift:435 (검사 예외: 같은 글)
        }
        // 이미 뜬 것은 그대로 두고, 새 것은 이번 주에 아직 안 떴을 때 하나만
        val out = ArrayList(cand.filter { log.first[it.id] != null })   // ios: Data/Remind.swift:435
        val c = cand.firstOrNull { log.first[it.id] == null }
        // ios: Data/Remind.swift:436,437
        if (log.analysisWeek != wk && c != null) {
            out.add(c); setLog { it.copy(analysisWeek = wk) }
        }
        return out   // ios: Data/Remind.swift:439
    }

    /** 설정·구독·개발자 소식 */
    // ios: Data/Remind.swift:443
    private fun news(): List<RemindItem> {
        val a = ArrayList<RemindItem>()   // ios: Data/Remind.swift:444
        val sub = Support.shared   // ios: Data/Remind.swift:445
        val p = sub.plan
        // ios: Data/Remind.swift:446
        if (p != null) {
            a.add(RemindItem(id = "sub.${p.name}", cat = RemindCat.news, text = "${p.nameText} 고마워요! 위젯, 목표·확률 전망, 서재 외전이 열려 있어요.", go = RemindGo.support))   // Data/Remind.swift:450 (검사 예외: 같은 글)
        } else if (sub.trialLeft == 1 && !sub.dev) {   // ios: Data/Remind.swift:448
            a.add(RemindItem(id = "trial.last", cat = RemindCat.news, text = "7일 무료가 오늘 끝나요. 내일부터 위젯과 목표·확률 전망은 구독하면 볼 수 있어요.", go = RemindGo.support))   // ios: Data/Remind.swift:449
        }
        // ios: Data/Remind.swift:451
        if (Sync.shared.state is Sync.State.failed) {
            a.add(RemindItem(id = "sync.fail", cat = RemindCat.news, text = "기기 동기화가 멈췄어요. 한번 확인해 볼래요?", go = RemindGo.sync, once = false))   // ios: Data/Remind.swift:452
        }
        // 기록을 시작하기 사흘 전부터의 개발자 소식만 (처음 깐 사람에게 지난 소식을 쏟지 않게)
        val from = Day.str(Instant.ofEpochMilli(((log.since - 3 * 86400) * 1000).toLong()))   // ios: Data/Remind.swift:455
        val ids = HashSet<String>()   // ios: Data/Remind.swift:456
        // ios: Data/Remind.swift:457
        for (n in RemindNews.app + site) { if (!(n.date >= from && n.date <= Day.today && ids.add(n.id))) continue
            a.add(RemindItem(id = "news.${n.id}", cat = RemindCat.news, text = n.text, go = RemindGo.of(n.go)))   // ios: Data/Remind.swift:458
        }
        // 증시 요약(블로그 새 글)은 뺐다 (2026-10-21)
        return a   // ios: Data/Remind.swift:461
    }

    /** 미래 설계 '2026 지구' 새 글 → 증시 요약: "제목 - 첫 문장". 올라온 지 10일까지만 (지나면 소식함에서도 빠진다). 누르면 웹 글로 */
    // ios: Data/Remind.swift:465
    private fun blogNews(): List<RemindItem> {
        val ids = blog.cats.filter { it.name == blogCat }.map { it.id }.toSet()   // ios: Data/Remind.swift:466
        val from = Day.str(Instant.now().minusSeconds((blogDays * 86400).toLong()))   // ios: Data/Remind.swift:467
        // ios: Data/Remind.swift:468
        return blog.posts.mapNotNull { p ->
            // ios: Data/Remind.swift:469
            val c = p.created?.take(10)
            if (!(ids.contains(p.cat) && c != null && c >= from && c <= Day.today)) return@mapNotNull null
            RemindItem(id = "blog.${p.id}", cat = RemindCat.market, text = if (p.lead.isEmpty()) p.title else "${p.title} - ${p.lead}", go = RemindGo.web("https://naeilo.com/web/#post=${p.id}"))   // ios: Data/Remind.swift:470,471
        }
    }

    /** 사이트 소식 파일 (6시간에 한 번) */
    // ios: Data/Remind.swift:476
    private suspend fun loadSite() {
        if (System.currentTimeMillis() - siteAt <= 6 * 3600 * 1000L) return   // ios: Data/Remind.swift:477
        siteAt = System.currentTimeMillis()   // ios: Data/Remind.swift:478
        loadBlog()   // ios: Data/Remind.swift:479
        // ios: Data/Remind.swift:480,481,482,483,484
        val d = get("app_news.json", timeoutMs = 20 * 1000, ok = 200) ?: return
        val list = runCatching { NJson.decodeFromString(ListSerializer(RemindNews.serializer()), d.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.IO) { Store.writeData(d, "app_news.json") }   // ios: Data/Remind.swift:485
        site = list   // ios: Data/Remind.swift:486
    }

    /** 미래 설계 글 묶음 (소식 파일과 같이 6시간에 한 번) */
    // ios: Data/Remind.swift:490
    private suspend fun loadBlog() {
        // ios: Data/Remind.swift:491,492,493,494,495
        val d = get("blog.json", timeoutMs = 20 * 1000, ok = 200) ?: return
        val b = runCatching { NJson.decodeFromString(BlogFile.serializer(), d.decodeToString()) }.getOrNull() ?: return
        withContext(Dispatchers.IO) { Store.writeData(d, "blog.json") }   // ios: Data/Remind.swift:496
        blog = b   // ios: Data/Remind.swift:497
    }
    /** 사이트 data/ 파일 받기 (캐시 없이). ok 가 아닌 응답이면 null */
    private suspend fun get(name: String, timeoutMs: Int, ok: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(Config.data, name).openConnection() as HttpURLConnection
            c.useCaches = false; c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            try { if (c.responseCode == ok) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
        }.getOrNull()
    }
}

// MARK: 일정 (naeilo.com 외부 요인의 날짜가 정해진 사건과 같은 규칙)

/** 웹 app.js EXT_EVENTS 중 확률 100%이고 날짜가 정해진 것 + 보유 종목 실적. 날짜 반복은 web/model.js occurrences 와 같은 방식 */
// ios: Data/Remind.swift:510
object MarketEvents {
    class Rule(val id: String, val target: String, val kind: String, val date: String, val rep: String)   // ios: Data/Remind.swift:511
    // ios: Data/Remind.swift:512
    val rules: List<Rule> = listOf(
        Rule(id = "x_fomc", target = "ALL", kind = "미국 FOMC", date = "2026-10-28", rep = "6w"),   // ios: Data/Remind.swift:513
        Rule(id = "x_cpi", target = "ALL", kind = "미국 CPI", date = "2026-10-14", rep = "monthly"),   // ios: Data/Remind.swift:514
        Rule(id = "x_jobs", target = "ALL", kind = "미국 고용", date = "2026-11-06", rep = "monthly"),   // ios: Data/Remind.swift:515
        Rule(id = "x_bok", target = "FX", kind = "한은 금리 결정", date = "2026-10-22", rep = "6w"),   // ios: Data/Remind.swift:516
        Rule(id = "x_dlv", target = "TSLA", kind = "테슬라 인도량", date = "2027-01-04", rep = "quarterly"),   // ios: Data/Remind.swift:517
        Rule(id = "x_gtc", target = "NVDA", kind = "엔비디아 GTC", date = "2027-03-16", rep = "yearly"),   // ios: Data/Remind.swift:518
        Rule(id = "x_opec", target = "ALL", kind = "OPEC+", date = "2026-11-30", rep = "semi"),   // ios: Data/Remind.swift:519
        Rule(id = "x_mid", target = "ALL", kind = "미국 중간선거", date = "2026-11-04", rep = "none"),   // ios: Data/Remind.swift:520
        Rule(id = "x_witch", target = "ALL", kind = "선물·옵션 만기", date = "2026-12-18", rep = "quarterly"),   // ios: Data/Remind.swift:521
        // 보유 종목 실적 (위젯 '다음' 줄이 쓰던 값)
        Rule(id = "e_drnk", target = "DRNK", kind = "드링커 실적", date = "2026-10-22", rep = "none"),   // ios: Data/Remind.swift:523
        Rule(id = "e_005930", target = "005930", kind = "삼성전자 실적", date = "2026-10-29", rep = "none"),   // ios: Data/Remind.swift:524
        Rule(id = "e_aapl", target = "AAPL", kind = "애플 실적", date = "2026-10-30", rep = "none"),   // ios: Data/Remind.swift:525
        Rule(id = "e_nvda", target = "NVDA", kind = "엔비디아 실적", date = "2026-11-19", rep = "none"),   // ios: Data/Remind.swift:526
    )
    class Hit(val id: String, val target: String, val day: String, val kind: String, val sub: String, val held: Boolean)   // ios: Data/Remind.swift:528

    /** 손 날짜에 실적 자료를 덮은 일정. 미국 종목 e_ 만 (DRNK·국내는 손 값 그대로) */
    val live: List<Rule> get() = rules.map(::earnRule)   // ios: Data/Remind.swift:531
    /** 실적 자료(받은 것·앱 안 견본)가 있으면: 기다리는 중이면 그 날, 오늘 발표면 오늘,
     *  아니면 다음 분기 = 작년 같은 분기 발표일 + 364일 (같은 요일), 문구에 '무렵' */
    // ios: Data/Remind.swift:534
    fun earnRule(r: Rule): Rule {
        if (!r.id.startsWith("e_") || r.target == "DRNK") return r   // ios: Data/Remind.swift:535
        val sym = Sample.symbol(r.target)?.takeIf { it.currency == Currency.usd } ?: return r   // ios: Data/Remind.swift:535
        // ios: Data/Remind.swift:536
        val k = EarningsFeed.key(sym) ?: return r
        val f = EarningsFeed.shared.file(k) ?: return r
        val last = f.quarters.lastOrNull() ?: return r
        f.pending?.announced?.let { return Rule(r.id, r.target, r.kind, it, "none") }   // ios: Data/Remind.swift:537
        if (last.announced >= Day.today) return Rule(r.id, r.target, r.kind, last.announced, "none")   // ios: Data/Remind.swift:538
        // 다음 분기의 작년 짝: 끝 날짜가 (마지막 분기 끝 + 약 91일) 보다 365±25일 앞
        val nextEnd = Earnings.dayNum(last.end) + 91   // ios: Data/Remind.swift:540
        val ly = f.quarters.firstOrNull { kotlin.math.abs(nextEnd - Earnings.dayNum(it.end) - 365) <= 25 } ?: return r   // ios: Data/Remind.swift:541
        val d = Day.date(ly.announced) ?: return r   // ios: Data/Remind.swift:542
        return Rule(r.id, r.target, "${r.kind} 무렵", Day.str(d.plusSeconds(364L * 86400)), "none")   // ios: Data/Remind.swift:543
    }

    /** from 날짜부터 days 일 안의 날짜 (from 포함) */
    // ios: Data/Remind.swift:547
    fun dates(r: Rule, from: String, until: String): List<String> {
        val step: Int? = mapOf("monthly" to 1, "quarterly" to 3, "semi" to 6, "yearly" to 12)[r.rep]   // ios: Data/Remind.swift:548
        val out = ArrayList<String>()   // ios: Data/Remind.swift:549
        val start = Day.date(r.date) ?: return emptyList()   // ios: Data/Remind.swift:550
        // ios: Data/Remind.swift:551
        for (k in 0 until 600) {
            // ios: Data/Remind.swift:552,553,554
            val d: Instant? = if (r.rep == "6w") start.plusSeconds(42L * k * 86400) else addMonths(r.date, k * (step ?: 0))
            val dd = d ?: break   // ios: Data/Remind.swift:555
            val s = Day.str(dd)   // ios: Data/Remind.swift:556
            if (s > until) break   // ios: Data/Remind.swift:557
            if (s >= from) out.add(s)   // ios: Data/Remind.swift:558
            if (r.rep == "none" || (r.rep != "6w" && step == null)) break   // ios: Data/Remind.swift:559
        }
        return out   // ios: Data/Remind.swift:561
    }
    /** 월 더하기: 말일을 넘으면 그 달 말일 (web addMonths 와 같다) */
    // ios: Data/Remind.swift:564,565,566,567,568,569,570,571
    private fun addMonths(s: String, n: Int): Instant? {
        if (Day.date(s) == null) return null
        val zone = ZoneId.of("Asia/Seoul")   // Day.fmt 의 시간대
        val d = LocalDate.parse(s); val day = d.dayOfMonth
        val m = d.withDayOfMonth(1).plusMonths(n.toLong()); val len = m.lengthOfMonth()
        return m.withDayOfMonth(min(day, len)).atStartOfDay(zone).toInstant()
    }

    /** 오늘부터 days 일 안의 일정. 내 종목(환율은 달러 종목) 관련이 먼저, 그다음 날짜순 */
    // ios: Data/Remind.swift:575
    fun upcoming(held: List<String>, days: Int): List<Hit> {
        val from = Day.today; val until = Day.str(Instant.now().plusSeconds(days.toLong() * 86400))   // ios: Data/Remind.swift:576
        val usd = held.any { Sample.symbol(it)?.currency == Currency.usd }   // ios: Data/Remind.swift:577
        val out = ArrayList<Hit>()   // ios: Data/Remind.swift:578
        // ios: Data/Remind.swift:579
        for (r in live) {
            val mine = held.contains(r.target) || (r.target == "FX" && usd)   // ios: Data/Remind.swift:580
            if (!(r.target == "ALL" || r.target == "FX" || mine)) continue   // ios: Data/Remind.swift:581
            val sub = if (r.target == "ALL") "전 종목" else if (r.target == "FX") "환율" else (Sample.symbol(r.target)?.short ?: r.target)   // ios: Data/Remind.swift:582
            for (d in dates(r, from = from, until = until)) out.add(Hit(id = r.id, target = r.target, day = d, kind = r.kind, sub = sub, held = held.contains(r.target)))   // ios: Data/Remind.swift:583
        }
        return out.sortedWith { a, b -> val ka = if (a.held) 0 else 1; val kb = if (b.held) 0 else 1; if (ka != kb) ka - kb else a.day.compareTo(b.day) }   // ios: Data/Remind.swift:585
    }

    /** 금리 결정 일정 (FOMC·한국은행)인지 */
    fun isRate(id: String): Boolean = id == "x_fomc" || id == "x_bok"   // ios: Data/Remind.swift:589
    /** 오늘부터 days 일 안의 금리 결정 일정, 날짜순 (보유 종목과 상관없이) */
    // ios: Data/Remind.swift:591
    fun rateUpcoming(days: Int): List<Hit> {
        val from = Day.today; val until = Day.str(Instant.now().plusSeconds(days.toLong() * 86400))   // ios: Data/Remind.swift:592
        // ios: Data/Remind.swift:593,594,595
        return rules.filter { isRate(it.id) }.flatMap { r ->
            dates(r, from = from, until = until).map { Hit(id = r.id, target = r.target, day = it, kind = r.kind, sub = if (r.target == "FX") "환율" else "전 종목", held = false) }
        }.sortedBy { it.day }
    }

    /** 일정이 내게 왜 중요한지 한 줄 (금리·물가·고용·실적 4종) */
    // ios: Data/Remind.swift:599,600
    fun why(id: String): String? = when (id) {
        "x_fomc", "x_bok" -> "금리가 바뀌면 환율이 움직이고, 미국 주식의 원화 평가액이 달라질 수 있어요."   // ios: Data/Remind.swift:601
        "x_cpi" -> "물가가 예상과 다르면 금리 전망이 바뀌어 주가가 크게 흔들릴 수 있어요."   // ios: Data/Remind.swift:602
        "x_jobs" -> "일자리 숫자는 금리 전망에 영향을 줘서 주가와 환율이 함께 움직일 수 있어요."   // ios: Data/Remind.swift:603
        else -> if (id.startsWith("e_")) "실적이 기대와 다르면 그 종목 값이 하루에 크게 움직일 수 있어요." else null   // ios: Data/Remind.swift:604
    }
    /** "내일", "10/22 (D-11)" 처럼 */
    // ios: Data/Remind.swift:608
    fun `when`(day: String): String {
        val n = Day.dday(day)   // ios: Data/Remind.swift:609
        return if (n == 0) "오늘" else if (n == 1) "내일" else "${Day.md(day)} D-$n"   // ios: Data/Remind.swift:610
    }
    /** 위젯 '다음' 줄: 30일 안의 첫 일정 (내 종목 먼저) */
    // ios: Data/Remind.swift:613
    fun widgetNext(held: List<String>): String? {
        val h = upcoming(held = held, days = 30).firstOrNull() ?: return null   // ios: Data/Remind.swift:614
        val n = Day.dday(h.day)   // ios: Data/Remind.swift:615
        return "${h.kind} " + (if (n == 0) "오늘" else "D-$n") + " (${Day.md(h.day)})"   // ios: Data/Remind.swift:616
    }
}

/** 소식을 눌렀을 때 그 화면으로 */
// ios: Data/Remind.swift:620,622,623
fun AppModel.go(g: RemindGo) {
    when (g) {
        RemindGo.nowhere -> return   // ios: Data/Remind.swift:624
        // ios: Data/Remind.swift:625 (UIApplication.shared.open: 브라우저로)
        is RemindGo.web -> runCatching { Defaults.ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(g.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        RemindGo.shelter -> { tab = Tab.home; homePath = listOf("shelter") }   // ios: Data/Remind.swift:626
        is RemindGo.char -> { tab = Tab.home; homePath = listOf("shelter", "char:${g.id}") }   // ios: Data/Remind.swift:627
        RemindGo.me -> { tab = Tab.home; homePath = listOf("me") }   // ios: Data/Remind.swift:628
        is RemindGo.reader -> { tab = Tab.home; homePath = listOf("shelter", "read:${g.i}") }   // ios: Data/Remind.swift:629
        RemindGo.board -> { tab = Tab.board; boardPath = emptyList() }   // ios: Data/Remind.swift:630
        RemindGo.week -> { tab = Tab.board; boardPath = emptyList() }   // ios: Data/Remind.swift:631
        RemindGo.calm -> { tab = Tab.analysis; analysisPath = listOf(AnalysisRoute.calm) }   // ios: Data/Remind.swift:632
        RemindGo.myPath -> { tab = Tab.analysis; analysisPath = listOf() }   // ios: Data/Remind.swift:633 내 길은 분석 탭 메인으로 옮겼다
        RemindGo.forecast -> { tab = Tab.analysis; analysisPath = listOf(AnalysisRoute.forecast) }   // ios: Data/Remind.swift:634
        RemindGo.rateScen -> { tab = Tab.analysis; analysisPath = listOf(AnalysisRoute.rateScen) }   // ios: Data/Remind.swift:635
        RemindGo.external -> { tab = Tab.analysis; analysisPath = listOf(AnalysisRoute.external) }   // ios: Data/Remind.swift:636
        // ios: Data/Remind.swift:637
        is RemindGo.hold -> {
            tab = Tab.hold   // ios: Data/Remind.swift:638
            holdPath = emptyList(); holdOpen = Sample.symbol(g.s)   // ios: Data/Remind.swift:639
        }
        RemindGo.support -> openSettings(SettingsRoute.support)   // ios: Data/Remind.swift:640
        RemindGo.widgets -> kotlinx.coroutines.MainScope().launch { kotlinx.coroutines.delay(350); widgetsOpen = true }   // ios: Data/Remind.swift:641 (0.35초)
        RemindGo.sync -> openSettings(SettingsRoute.sync)   // ios: Data/Remind.swift:642
        RemindGo.alerts -> openSettings(SettingsRoute.alerts)   // ios: Data/Remind.swift:643
        RemindGo.krSum -> krSumAsk = true   // ios: Data/Remind.swift:644
    }
}

