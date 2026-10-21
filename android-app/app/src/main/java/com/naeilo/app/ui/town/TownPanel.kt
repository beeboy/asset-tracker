package com.naeilo.app.ui.town

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.MeColor
import com.naeilo.app.data.MeGroup
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.SheetBar
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.rememberPrefString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import kotlin.math.max
import kotlin.math.min

// 동네 대사창 아래 칸: 기본(오늘의 한 줄 · 내가 할 말 고르기) / 우리 사이 / 꾸미기.
// 글은 미리 써 둔 작은 표에서만 고른다 (새로 받는 데이터 없음). 한 화면에 들어가게 높이를 고정

/** TownPanel.height (Swift static let) */
object TownPanel {
    val height: Dp = 196.dp   // ios: Views/TownPanel.swift:11
}

// ios: Views/TownPanel.swift:5
@Composable
fun TownPanel(
    res: TownLook.Resident,   // ios: Views/TownPanel.swift:6
    tab: MutableState<Int>,                 // ios: Views/TownPanel.swift:7 0 기본, 1 우리 사이, 2 꾸미기
    line: MutableState<String?>,            // ios: Views/TownPanel.swift:8 대사창에 띄울 주민 대답
    look: MutableState<TownBook.Note?>,     // ios: Views/TownPanel.swift:9 꾸미기 미리 보기
    ask: MutableState<Int>,                 // ios: Views/TownPanel.swift:10 늘면 대사창 답장 칸을 연다
    height: Dp = TownPanel.height,     // ios: Views/TownPanel.swift:12 우리 사이·꾸미기는 남는 높이까지 늘려 받는다
) {
    val special = res.id == TownLook.mayorID || res.id == TownLook.warshID   // ios: Views/TownPanel.swift:13,14
    val cream = Theme.cream; val brown = Theme.brown
    Box(Modifier
        .fillMaxWidth().height(height)   // ios: Views/TownPanel.swift:22 frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .top)
        .background(cream, RoundedCornerShape(10.dp))   // ios: Views/TownPanel.swift:23
        .strokeR(brown, 10.dp, 2.dp)   // ios: Views/TownPanel.swift:24
        .padding(10.dp),   // ios: Views/TownPanel.swift:21
        contentAlignment = Alignment.TopStart) {
        key(res.id) {      // ios: Views/TownPanel.swift:20 다른 주민에게 말 걸면 고를 말·꾸미기 칸을 처음부터
            if (tab.value == 1 && !special) TownUsPanel(res = res)   // ios: Views/TownPanel.swift:15,16
            else if (tab.value == 2 && !special) TownDressPanel(res = res, line = line, look = look)   // ios: Views/TownPanel.swift:17
            else TownHomePanel(res = res, line = line, ask = ask)   // ios: Views/TownPanel.swift:18
        }
    }
}

// ── 기본: 오늘의 한 줄 · 내가 할 말 고르기 ──

// ios: Views/TownPanel.swift:30
@Composable
private fun TownHomePanel(
    res: TownLook.Resident,   // ios: Views/TownPanel.swift:31
    line: MutableState<String?>,   // ios: Views/TownPanel.swift:32
    ask: MutableState<Int>,   // ios: Views/TownPanel.swift:33
) {
    var opts by remember { mutableStateOf<List<String>>(emptyList()) }   // ios: Views/TownPanel.swift:34 @State private var opts
    var saidDone by remember { mutableStateOf(false) }   // ios: Views/TownPanel.swift:35 @State private var saidDone
    val log = TownLog.shared   // ios: Views/TownPanel.swift:36
    val done = log.log(res.id).lastOrNull()?.let { it.day == TownLog.day && it.me != null } ?: false   // ios: Views/TownPanel.swift:37,38
    val brown = Theme.brown; val card = Theme.card
    /** 오늘의 한 줄을 끝냈으면 "다 했어!"를 후보 맨 앞에 (한 번 말하면 빠짐) */
    // ios: Views/TownPanel.swift:78
    fun shownOpts(done: Boolean): List<String> {
        val o = if (opts.isEmpty()) TownChat.start() else opts   // ios: Views/TownPanel.swift:79
        return if (done && !saidDone) listOf(TownChat.doneKey) + o.take(2) else o.take(3)   // ios: Views/TownPanel.swift:80
    }
    // ios: Views/TownPanel.swift:82
    fun pick(o: String) {
        if (o == TownChat.doneKey) saidDone = true   // ios: Views/TownPanel.swift:83
        val n = TownChat.tree[o]   // ios: Views/TownPanel.swift:84
        line.value = n?.say ?: "그렇군요!"   // ios: Views/TownPanel.swift:85
        opts = n?.next ?: TownChat.start()   // ios: Views/TownPanel.swift:86
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {   // ios: Views/TownPanel.swift:39
        Column(Modifier
            .plainClickable { if (!done) ask.value += 1 }   // ios: Views/TownPanel.swift:40,60 Button · buttonStyle(.plain)
            .background(card.copy(alpha = 0.6f), RoundedCornerShape(8.dp))   // ios: Views/TownPanel.swift:57
            .strokeR(brown.copy(alpha = 0.5f), 8.dp)   // ios: Views/TownPanel.swift:58
            .padding(10.dp),   // ios: Views/TownPanel.swift:56
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {   // ios: Views/TownPanel.swift:41
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:42
                T("오늘의 한 줄", style = appFont(12f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:43
                T(if (done) "한 줄 답 남겼어요 ✓" else "대사에 한 줄 답 남기기 ›", style = appFont(14f, FontWeight.Bold), color = Theme.ink, maxLines = 1)   // ios: Views/TownPanel.swift:44
                Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/TownPanel.swift:45 Spacer(minLength: 0)
                T(if (done) "1/1" else "0/1", style = appFont(13f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:46
            }
            // ios: Views/TownPanel.swift:48,49 GeometryReader → 가득 찬 줄
            Box(Modifier.fillMaxWidth().height(6.dp)) {   // ios: Views/TownPanel.swift:54
                Box(Modifier.matchParentSize().background(brown.copy(alpha = 0.18f), CapsuleShape))   // ios: Views/TownPanel.swift:50
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (done) 1f else 0f).background(brown, CapsuleShape))   // ios: Views/TownPanel.swift:51 width: done ? g.size.width : 0
            }
        }
        T("내가 할 말 고르기", Modifier.padding(top = 4.dp), style = appFont(12f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:61
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:62
            for (o in shownOpts(done)) {   // ios: Views/TownPanel.swift:63
                Box(Modifier.weight(1f)
                    .defaultMinSize(minHeight = 48.dp)   // ios: Views/TownPanel.swift:67 frame(maxWidth: .infinity, minHeight: 48)
                    .background(Color(red = 0.10f, green = 0.11f, blue = 0.20f), RoundedCornerShape(8.dp))   // ios: Views/TownPanel.swift:68
                    .plainClickable { pick(o) },   // ios: Views/TownPanel.swift:64,70 Button · buttonStyle(.plain)
                    contentAlignment = Alignment.Center) {
                    FitText(o, style = appFont(14f, FontWeight.Bold), color = if (o == TownChat.doneKey) Color(red = 1f, green = 0.85f, blue = 0.4f) else Color.White,   // ios: Views/TownPanel.swift:65
                        maxLines = 1, minScale = 0.75f, modifier = Modifier.padding(horizontal = 4.dp))   // ios: Views/TownPanel.swift:66 lineLimit(1).minimumScaleFactor(0.75).padding(.horizontal, 4)
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (opts.isEmpty()) opts = TownChat.start() }   // ios: Views/TownPanel.swift:74
    var prevDone by remember { mutableStateOf(done) }
    LaunchedEffect(done) { if (done != prevDone) { prevDone = done; if (done) line.value = TownChat.praise } }   // ios: Views/TownPanel.swift:75 onChange(of: done)
}

/** 내가 할 말 → 주민 대답 · 다음 후보. 미리 써 둔 작은 표 (시장 이야기 없음) */
// ios: Views/TownPanel.swift:91
object TownChat {
    val doneKey = "다 했어!"   // ios: Views/TownPanel.swift:92
    val praise = "한 줄 남겨 줘서 고마워요! 오늘 할 일 끝, 대단해요!"   // ios: Views/TownPanel.swift:93
    private val bye = "또 올게"   // ios: Views/TownPanel.swift:94
    // ios: Views/TownPanel.swift:95
    fun start(d: Date = Date()): List<String> =
        if (Calendar.getInstance().apply { time = d }.get(Calendar.HOUR_OF_DAY) < 15) listOf("좋은 아침!", "잘 잤어?", "오늘 뭐 해?") else listOf("오늘 어땠어?", "저녁 먹었어?", "잘 자!")   // ios: Views/TownPanel.swift:96
    /** (say: 주민 대답, next: 다음 후보) */
    data class Node(val say: String, val next: List<String>)
    val tree: Map<String, Node> = mapOf(   // ios: Views/TownPanel.swift:98
        "좋은 아침!" to Node("좋은 아침이에요! 오늘도 천천히 가요.", listOf("기분은 어때?", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:99
        "잘 잤어?" to Node("푹 잤어요. 꿈에 큰 나무가 나왔어요!", listOf("무슨 나무?", "좋은 꿈이네", bye)),   // ios: Views/TownPanel.swift:100
        "오늘 뭐 해?" to Node("동네 한 바퀴 걸을 거예요. 같이 갈래요?", listOf("좋아!", "다음에", bye)),   // ios: Views/TownPanel.swift:101
        "오늘 어땠어?" to Node("조용한 하루였어요. 그런 날도 좋죠.", listOf("나도 그래", "기분은 어때?", "잘 자!")),   // ios: Views/TownPanel.swift:102
        "저녁 먹었어?" to Node("방금 먹었어요. 배불러요!", listOf("뭐 먹었어?", "부럽다", "잘 자!")),   // ios: Views/TownPanel.swift:103
        "잘 자!" to Node("잘 자요. 내일 또 봐요!", listOf("내일 봐", "기분은 어때?", bye)),   // ios: Views/TownPanel.swift:104
        "기분은 어때?" to Node("음… 맑음이에요. 당신은요?", listOf("나도 맑음", "조금 흐림", bye)),   // ios: Views/TownPanel.swift:105
        "날씨 좋다" to Node("그쵸? 이런 날엔 산책이 최고예요.", listOf("좋아!", "기분은 어때?", bye)),   // ios: Views/TownPanel.swift:106
        "무슨 나무?" to Node("열매가 반짝이는 나무요. 천천히 자라더라고요.", listOf("좋은 꿈이네", "기분은 어때?", bye)),   // ios: Views/TownPanel.swift:107
        "좋은 꿈이네" to Node("그쵸? 오늘은 좋은 일이 있을 것 같아요.", listOf("기분은 어때?", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:108
        "좋아!" to Node("신난다! 천천히 걸어요.", listOf("기분은 어때?", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:109
        "다음에" to Node("괜찮아요. 언제든 불러요!", listOf("기분은 어때?", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:110
        "나도 그래" to Node("우리 잘 맞네요!", listOf("기분은 어때?", "잘 자!", bye)),   // ios: Views/TownPanel.swift:111
        "뭐 먹었어?" to Node("따끈한 국밥이요. 최고였어요!", listOf("부럽다", "잘 자!", bye)),   // ios: Views/TownPanel.swift:112
        "부럽다" to Node("다음엔 같이 먹어요!", listOf("좋아!", "잘 자!", bye)),   // ios: Views/TownPanel.swift:113
        "내일 봐" to Node("꼭이에요! 기다릴게요.", listOf("기분은 어때?", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:114
        "나도 맑음" to Node("좋다! 맑은 날끼리 하이파이브!", listOf("날씨 좋다", "오늘 뭐 해?", bye)),   // ios: Views/TownPanel.swift:115
        "조금 흐림" to Node("그럴 때도 있죠. 여기서 좀 쉬다 가요.", listOf("고마워", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:116
        "고마워" to Node("천만에요. 언제든 와요!", listOf("기분은 어때?", "날씨 좋다", bye)),   // ios: Views/TownPanel.swift:117
        bye to Node("네, 언제든 와요!", emptyList()),   // ios: Views/TownPanel.swift:118
        doneKey to Node("벌써요? 대단해요! 오늘 한 줄 끝!", listOf("고마워", "기분은 어때?", bye)),   // ios: Views/TownPanel.swift:119
    )
}

// ── 우리 사이 ──

/** 사이 단계: 함께한 날 + 나눈 대화 + 남긴 답×2 로 점수 (저장 없이 그때그때 계산) */
// ios: Views/TownPanel.swift:126
object TownBond {
    val names = listOf("이웃", "친구", "단짝", "절친", "찐친")   // ios: Views/TownPanel.swift:127
    val need = listOf(0, 10, 30, 60, 100)   // ios: Views/TownPanel.swift:128
    const val bestLv = 4       // ios: Views/TownPanel.swift:129 절친 (꾸미기 잠긴 칸이 열림)
    // ios: Views/TownPanel.swift:130
    fun days(n: TownBook.Note): Int =
        if (n.moved.isEmpty()) 0 else TownLog.age(n.moved) - TownLog.age(n.left ?: TownLog.day) + 1   // ios: Views/TownPanel.swift:131
    // ios: Views/TownPanel.swift:133
    fun points(id: String): Int {
        val l = TownLog.shared.log(id)   // ios: Views/TownPanel.swift:134
        return days(TownBook.shared.note(id)) + l.size + l.count { it.me != null } * 2   // ios: Views/TownPanel.swift:135
    }
    fun level(p: Int): Int = (need.indexOfLast { p >= it }.takeIf { it >= 0 } ?: 0) + 1   // ios: Views/TownPanel.swift:137
    /** 오늘부터 거꾸로 하루도 안 빠지고 말 건 날 수 */
    // ios: Views/TownPanel.swift:139
    fun streak(id: String): Int {
        val days = TownLog.shared.log(id).map { it.day }.toSet()   // ios: Views/TownPanel.swift:140
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd")   // ios: Views/TownPanel.swift:141
        var d = LocalDate.now(); var n = 0   // ios: Views/TownPanel.swift:142
        while (days.contains(f.format(d))) { n += 1; d = d.plusDays(-1) }   // ios: Views/TownPanel.swift:143 byAdding: .day, value: -1
        return n   // ios: Views/TownPanel.swift:144
    }
}

// ios: Views/TownPanel.swift:148
@Composable
private fun TownUsPanel(
    res: TownLook.Resident,   // ios: Views/TownPanel.swift:149
) {
    var all by remember { mutableStateOf(false) }   // ios: Views/TownPanel.swift:150 @State private var all
    val n = TownBook.shared.note(res.id)   // ios: Views/TownPanel.swift:151,152
    val log = TownLog.shared.log(res.id)   // ios: Views/TownPanel.swift:153
    val p = TownBond.points(res.id)   // ios: Views/TownPanel.swift:154
    val lv = TownBond.level(p)   // ios: Views/TownPanel.swift:155
    val lo = TownBond.need[lv - 1]; val hi = if (lv < TownBond.need.size) TownBond.need[lv] else null   // ios: Views/TownPanel.swift:156
    val past = log.lastOrNull { it.me != null }   // ios: Views/TownPanel.swift:157 내가 답한 대화만
    val brown = Theme.brown; val ink = Theme.ink; val card = Theme.card
    // ios: Views/TownPanel.swift:206
    @Composable
    fun cell(k: String, v: String, modifier: Modifier) {
        Column(modifier
            .background(card.copy(alpha = 0.6f), RoundedCornerShape(8.dp))   // ios: Views/TownPanel.swift:212
            .padding(vertical = 4.dp),   // ios: Views/TownPanel.swift:211 frame(maxWidth: .infinity).padding(.vertical, 4)
            verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/TownPanel.swift:207
            T(v, style = appFont(15f, FontWeight.Bold), color = ink)   // ios: Views/TownPanel.swift:208
            T(k, style = appFont(11f), color = brown)   // ios: Views/TownPanel.swift:209
        }
    }
    /** 기억 칩: 별명 · 최근 내 답 둘 · 받은 별사탕 · 이사 온 날 (있는 것만) */
    // ios: Views/TownPanel.swift:215
    fun memo(n: TownBook.Note, log: List<TownLog.Entry>): List<String> {
        val m = ArrayList<String>()   // ios: Views/TownPanel.swift:216
        n.name?.let { s -> m.add("별명 $s") }   // ios: Views/TownPanel.swift:217
        m += log.mapNotNull { it.me }.takeLast(2).reversed().map { "“${it.take(12)}”" }   // ios: Views/TownPanel.swift:218
        n.gifts?.let { g -> if (g > 0) m.add("별사탕 ${g}개 받음") }   // ios: Views/TownPanel.swift:219
        if (n.moved.isNotEmpty()) m.add(n.moved.drop(5).replace("-", ".") + " 이사")   // ios: Views/TownPanel.swift:220
        return if (m.isEmpty()) listOf("말을 걸면 하나씩 기억해요") else m   // ios: Views/TownPanel.swift:221
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.Start) {   // ios: Views/TownPanel.swift:158
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/TownPanel.swift:159 alignment: .firstTextBaseline
            T("${TownBond.names[lv - 1]} Lv.$lv", Modifier.alignByBaseline(), style = appFont(16f, FontWeight.Bold), color = ink)   // ios: Views/TownPanel.swift:160
            Spacer(Modifier.weight(1f))   // ios: Views/TownPanel.swift:161
            T(hi?.let { "${TownBond.names[lv]}까지 ${it - p}점" } ?: "최고 단계예요", Modifier.alignByBaseline(), style = appFont(12f), color = brown)   // ios: Views/TownPanel.swift:162
        }
        // ios: Views/TownPanel.swift:164,165 GeometryReader → 가득 찬 줄의 몫
        Box(Modifier.fillMaxWidth().height(8.dp)) {   // ios: Views/TownPanel.swift:170
            Box(Modifier.matchParentSize().background(brown.copy(alpha = 0.18f), CapsuleShape))   // ios: Views/TownPanel.swift:166
            Box(Modifier.fillMaxHeight().fillMaxWidth(hi?.let { (p - lo).toFloat() / max(1, it - lo).toFloat() } ?: 1f).background(brown, CapsuleShape))   // ios: Views/TownPanel.swift:167
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:171
            cell("함께한 날", "${TownBond.days(n)}일", Modifier.weight(1f))   // ios: Views/TownPanel.swift:172
            cell("나눈 대화", "${log.size}번", Modifier.weight(1f))   // ios: Views/TownPanel.swift:173
            cell("연속 방문", "${TownBond.streak(res.id)}일", Modifier.weight(1f))   // ios: Views/TownPanel.swift:174
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:176,177 ScrollView(.horizontal, showsIndicators: false)
            T("기억해요", style = appFont(11f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:178
            for (t in memo(n, log)) {   // ios: Views/TownPanel.swift:179
                T(t, Modifier
                    .background(card.copy(alpha = 0.7f), CapsuleShape)   // ios: Views/TownPanel.swift:182
                    .strokeR(brown.copy(alpha = 0.5f), null)   // ios: Views/TownPanel.swift:183 Capsule().stroke
                    .padding(horizontal = 8.dp, vertical = 4.dp),   // ios: Views/TownPanel.swift:181
                    style = appFont(12f), color = ink, maxLines = 1)   // ios: Views/TownPanel.swift:180
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:187
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = Alignment.Start) {   // ios: Views/TownPanel.swift:188
                T("최근 대화", style = appFont(11f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:189
                T(past?.let { "나: " + (it.me ?: "") } ?: "아직 지난 대화가 없어요",   // ios: Views/TownPanel.swift:190
                    style = appFont(13f), color = ink, maxLines = 1)   // ios: Views/TownPanel.swift:191
            }
            Spacer(Modifier.widthIn(min = 0.dp))   // ios: Views/TownPanel.swift:193 Spacer(minLength: 0)
            Box(Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 44.dp).plainClickable { all = true }, contentAlignment = Alignment.Center) {   // ios: Views/TownPanel.swift:194,195 Button · frame(minWidth: 64, minHeight: 44)
                T("전체 보기", style = appFont(13f, FontWeight.Bold), color = brown)
            }
        }
    }
    // ios: Views/TownPanel.swift:198,199,203 sheet · NavigationStack · presentationDetents([.medium, .large]) → 절반 높이 시트
    Sheet(show = all, onDismiss = { all = false }, medium = true) {
        SheetBar(TownLook.shown(res, n))   // ios: Views/TownPanel.swift:201 navigationTitle · inline
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopStart) { TownPast(id = res.id) }   // ios: Views/TownPanel.swift:200
    }
}

// ── 꾸미기 ──

/** 꾸밀 거리: 모습(부품 조합, 공짜) · 모자 · 모자 색 · 소품 · 동네 배경. 가격은 별사탕, 산 것은 모든 주민이 같이 쓴다 */
// ios: Views/TownPanel.swift:228
private data class TownItem(
    val kind: Int, val i: Int, val name: String, val price: Int, val lock: Boolean,   // ios: Views/TownPanel.swift:229
) {
    val id: String get() = "$kind.$i"   // ios: Views/TownPanel.swift:230
}

/** TownDressPanel 의 Swift static let 들 */
private object TownDressPanel {
    val kinds = listOf("모자", "색", "소품", "동네", "모습")   // ios: Views/TownPanel.swift:242
    val tabs = listOf(4, 0, 1, 2, 3)   // ios: Views/TownPanel.swift:243
    val hatPrice = listOf(0, 0, 0, 3, 1, 2)   // ios: Views/TownPanel.swift:244
    val colorNames = listOf("노랑", "빨강", "파랑", "초록", "보라", "하양", "검정")   // ios: Views/TownPanel.swift:245
    /** 모습: 머리 모양 · 머리색 · 피부 · 윗옷 · 아래옷을 ◀ ▶ 로 넘겨 고른다 (유명인은 머리를 따로 그려 피부·옷만) */
    data class PartRow(val key: String, val label: String)
    val partRows: List<PartRow> = listOf(PartRow("hair", "머리 모양"), PartRow("color", "머리색"), PartRow("skin", "피부"), PartRow("top", "윗옷"), PartRow("bottom", "아래옷"))   // ios: Views/TownPanel.swift:291
}

// ios: Views/TownPanel.swift:233
@Composable
private fun TownDressPanel(
    res: TownLook.Resident,   // ios: Views/TownPanel.swift:234
    line: MutableState<String?>,   // ios: Views/TownPanel.swift:235
    look: MutableState<TownBook.Note?>,   // ios: Views/TownPanel.swift:236
) {
    var bg by townPrefInt("town.bg", 0)   // ios: Views/TownPanel.swift:237 @AppStorage("town.bg")
    var ownedRaw by rememberPrefString("town.owned", "")   // ios: Views/TownPanel.swift:238 @AppStorage("town.owned")
    var kind by remember { mutableIntStateOf(4) }          // ios: Views/TownPanel.swift:239 @State private var kind = 4 (모습이 맨 앞에 보인다)
    var bg0 by remember { mutableStateOf<Int?>(null) }       // ios: Views/TownPanel.swift:240 @State private var bg0 고르기 전 배경 (되돌리기)
    var name by remember { mutableStateOf(false) }   // ios: Views/TownPanel.swift:241 @State private var name
    val book = TownBook.shared   // ios: Views/TownPanel.swift:246
    val brown = Theme.brown; val ink = Theme.ink; val card = Theme.card; val teal = Theme.teal

    // ios: Views/TownPanel.swift:323
    fun step(key: String, cur: Int, cnt: Int, d: Int, n: TownBook.Note) {
        if (cnt <= 0) return   // ios: Views/TownPanel.swift:324 guard cnt > 0
        val p = (n.parts ?: emptyMap()).toMutableMap()   // ios: Views/TownPanel.swift:325 var m = n, p = n.parts ?? [:]
        p[key] = (cur - 1 + d + cnt) % cnt + 1   // ios: Views/TownPanel.swift:326
        look.value = n.copy(parts = p)   // ios: Views/TownPanel.swift:327 m.parts = p; look = m
        line.value = "이런 모습 어때요?"   // ios: Views/TownPanel.swift:328
    }

    val owned: Set<String> = ownedRaw.split(",").filter { it.isNotEmpty() }.toSet()   // ios: Views/TownPanel.swift:331
    // ios: Views/TownPanel.swift:332
    fun items(k: Int, lv: Int): List<TownItem> = when (k) {   // ios: Views/TownPanel.swift:333
        0 -> TownFace.hats.mapIndexed { i, s -> TownItem(kind = 0, i = i, name = s, price = TownDressPanel.hatPrice[min(i, TownDressPanel.hatPrice.size - 1)], lock = s == "왕관" && lv < TownBond.bestLv) }   // ios: Views/TownPanel.swift:334
        1 -> TownFace.tints.indices.take(6).map { i -> TownItem(kind = 1, i = i, name = TownDressPanel.colorNames[min(i, TownDressPanel.colorNames.size - 1)], price = if (i < 3) 0 else 1, lock = false) }   // ios: Views/TownPanel.swift:335
        2 -> TownFace.propNames.mapIndexed { i, s -> TownItem(kind = 2, i = i, name = s, price = i, lock = false) }   // ios: Views/TownPanel.swift:336 안경 1 · 목도리 2 · 풍선 3
        else -> TownGround.names.mapIndexed { i, s -> TownItem(kind = 3, i = i, name = s, price = 0, lock = false) }   // ios: Views/TownPanel.swift:337
    }
    fun has(it: TownItem): Boolean = it.price == 0 || owned.contains(it.id)   // ios: Views/TownPanel.swift:340
    // ios: Views/TownPanel.swift:341
    fun on(it: TownItem, n: TownBook.Note): Boolean =
        when (it.kind) { 0 -> n.hat == it.i; 1 -> n.hat > 0 && n.tint == it.i; 2 -> (n.prop ?: 0) == it.i; 3 -> bg == it.i; else -> false }   // ios: Views/TownPanel.swift:342

    // ios: Views/TownPanel.swift:371
    fun pick(it: TownItem, n: TownBook.Note) {
        if (it.lock) { line.value = "${it.name}은 절친이 되면 써 볼게요!"; return }   // ios: Views/TownPanel.swift:372
        var m = n   // ios: Views/TownPanel.swift:373
        when (it.kind) {   // ios: Views/TownPanel.swift:374
            0 -> m = m.copy(hat = it.i)   // ios: Views/TownPanel.swift:375
            1 -> { if (m.hat == 0) m = m.copy(hat = 1); m = m.copy(tint = it.i) }   // ios: Views/TownPanel.swift:376
            2 -> m = m.copy(prop = if (it.i == 0) null else it.i)   // ios: Views/TownPanel.swift:377
            else -> { if (bg0 == null) bg0 = bg; bg = it.i }   // ios: Views/TownPanel.swift:378
        }
        if (it.kind != 3) look.value = m   // ios: Views/TownPanel.swift:380
        line.value = if (it.i == 0 && it.kind == 0) "모자 없이도 괜찮죠?" else if (it.i == 0 && it.kind == 2) "소품 없이 가볍게!" else "${it.name} 어때요? 잘 어울려요!"   // ios: Views/TownPanel.swift:381
    }
    // ios: Views/TownPanel.swift:383
    fun revert() {
        look.value = null   // ios: Views/TownPanel.swift:384
        bg0?.let { b -> bg = b; bg0 = null }   // ios: Views/TownPanel.swift:385
    }
    /** 이대로 입기: 안 산 것은 별사탕으로 사고 수첩에 적는다 */
    // ios: Views/TownPanel.swift:388
    fun wear(n: TownBook.Note) {
        val lv = TownBond.level(TownBond.points(res.id))   // ios: Views/TownPanel.swift:389
        val all = (0 until 4).flatMap { items(it, lv) }   // ios: Views/TownPanel.swift:390
        val need = all.filter { on(it, n) && !has(it) && !it.lock }   // ios: Views/TownPanel.swift:391
        val cost = need.fold(0) { a, b -> a + b.price }   // ios: Views/TownPanel.swift:392 reduce(0)
        if (!(cost == 0 || book.spend(cost))) { line.value = "별사탕이 ${cost - book.candy}개 모자라요. 답을 남기면 생겨요!"; return }   // ios: Views/TownPanel.swift:393
        if (need.isNotEmpty()) ownedRaw = (owned + need.map { it.id }).sorted().joinToString(",")   // ios: Views/TownPanel.swift:394
        book.set(res.id, n)   // ios: Views/TownPanel.swift:395
        look.value = null; bg0 = null   // ios: Views/TownPanel.swift:396
        line.value = if (cost > 0) "고마워요! 별사탕 ${cost}개로 샀어요. 마음에 들어요!" else "고마워요! 마음에 들어요!"   // ios: Views/TownPanel.swift:397
    }

    // ios: Views/TownPanel.swift:292
    @Composable
    fun parts(n: TownBook.Note) {
        val star = TownStar.find(res.id) != null   // ios: Views/TownPanel.swift:293
        val me = TownLook.char(res.id, n)   // ios: Views/TownPanel.swift:294
        // 두 줄로: 다섯 칸 + 처음 모습으로
        val rows = TownDressPanel.partRows.filter { !star || (it.key != "hair" && it.key != "color") }   // ios: Views/TownPanel.swift:297
        // ios: Views/TownPanel.swift:296 LazyVGrid 두 칸 (칸 사이 6, 줄 사이 8)
        VGrid(count = 2, hSpacing = 6.dp, vSpacing = 8.dp, n = rows.size + 1) { idx, mod ->
            if (idx < rows.size) {
                val r = rows[idx]
                val cnt = if (r.key == "skin") MeColor.skin.size else MeGroup.entries.firstOrNull { it.raw == r.key }?.count ?: 1   // ios: Views/TownPanel.swift:298
                val cur = me.parts[r.key] ?: 1   // ios: Views/TownPanel.swift:299
                val nm = if (r.key == "skin") MeColor.skinNames[max(0, min(cur, cnt) - 1)] else MeGroup.entries.firstOrNull { it.raw == r.key }?.name(cur) ?: ""   // ios: Views/TownPanel.swift:300
                Row(mod.height(40.dp).background(card.copy(alpha = 0.6f), RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:301,312,313
                    Box(Modifier.size(36.dp, 40.dp).semantics { contentDescription = "${r.label} 앞으로"; role = Role.Button }.plainClickable { step(r.key, cur, cnt, -1, n) }, contentAlignment = Alignment.Center) {   // ios: Views/TownPanel.swift:302,303
                        T("◀", style = appFont(12f, FontWeight.Bold), color = brown)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/TownPanel.swift:304,308
                        T(r.label, style = appFont(10f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:305
                        FitText(nm, style = appFont(12f, FontWeight.Bold), minScale = 0.7f, maxLines = 1, color = ink)   // ios: Views/TownPanel.swift:306
                    }
                    Box(Modifier.size(36.dp, 40.dp).semantics { contentDescription = "${r.label} 다음"; role = Role.Button }.plainClickable { step(r.key, cur, cnt, 1, n) }, contentAlignment = Alignment.Center) {   // ios: Views/TownPanel.swift:309,310
                        T("▶", style = appFont(12f, FontWeight.Bold), color = brown)
                    }
                }
            } else {
                val off = n.parts == null
                Box(mod.defaultMinSize(minHeight = 40.dp)   // ios: Views/TownPanel.swift:317 frame(maxWidth: .infinity, minHeight: 40)
                    .strokeR(brown.copy(alpha = 0.5f), 8.dp, 1.dp)   // ios: Views/TownPanel.swift:318
                    .then(if (off) Modifier else Modifier.plainClickable { look.value = n.copy(parts = null); line.value = "처음 모습이에요." }),   // ios: Views/TownPanel.swift:315,320 Button · disabled(n.parts == nil)
                    contentAlignment = Alignment.Center) {
                    T("처음 모습으로", style = appFont(12f, FontWeight.Bold), color = brown.copy(alpha = if (n.parts == null) 0.4f else 1f))   // ios: Views/TownPanel.swift:316
                }
            }
        }
    }

    // ios: Views/TownPanel.swift:344
    @Composable
    fun cell(it: TownItem, n: TownBook.Note, mod: Modifier) {
        val wearing = on(it, n)   // ios: Views/TownPanel.swift:345
        Column(mod
            .defaultMinSize(minHeight = 40.dp)   // ios: Views/TownPanel.swift:364 frame(maxWidth: .infinity, minHeight: 40)
            .background(if (it.lock) Color.Transparent else card.copy(alpha = if (wearing) 0.95f else 0.6f), RoundedCornerShape(8.dp))   // ios: Views/TownPanel.swift:365
            .strokeR(if (wearing) teal else brown.copy(alpha = 0.5f), 8.dp, if (wearing) 2.dp else 1.dp, dash = if (it.lock) floatArrayOf(4f, 3f) else null)   // ios: Views/TownPanel.swift:366,367
            .plainClickable { pick(it, n) },   // ios: Views/TownPanel.swift:346,369 Button · buttonStyle(.plain)
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/TownPanel.swift:347
            if (it.kind == 1) {   // ios: Views/TownPanel.swift:348
                val c = TownFace.tints[it.i].toLong()   // ios: Views/TownPanel.swift:349
                Box(Modifier.size(16.dp, 16.dp).strokeR(Color.Black.copy(alpha = 0.2f), null)   // ios: Views/TownPanel.swift:351 frame(width: 16, height: 16) · overlay(Circle().stroke(Color.black.opacity(0.2)))
                    .background(Color(red = (c shr 16 and 0xFF) / 255f, green = (c shr 8 and 0xFF) / 255f, blue = (c and 0xFF) / 255f), CircleShape))   // ios: Views/TownPanel.swift:350
            } else if ((it.kind == 0 || it.kind == 2) && it.i > 0) {   // ios: Views/TownPanel.swift:352
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:353
                    TownItemIcon(px = if (it.kind == 0) TownFace.pixels(it.i, TownFace.tints[0]) else TownFace.props(it.i) + TownFace.thin(it.i),   // ios: Views/TownPanel.swift:354
                        modifier = Modifier.size(18.dp, 18.dp).alpha(if (it.lock) 0.4f else 1f))   // ios: Views/TownPanel.swift:355
                    FitText(it.name, style = appFont(13f, FontWeight.Bold), minScale = 0.7f, maxLines = 1, color = if (it.lock) brown.copy(alpha = 0.5f) else ink)   // ios: Views/TownPanel.swift:356
                }
            } else {   // ios: Views/TownPanel.swift:358
                FitText(it.name, style = appFont(13f, FontWeight.Bold), minScale = 0.7f, maxLines = 1, color = if (it.lock) brown.copy(alpha = 0.5f) else ink)   // ios: Views/TownPanel.swift:359
            }
            T(if (it.lock) "절친 때 열림" else if (wearing) "입는 중" else if (has(it)) (if (it.kind == 1) it.name else "보유") else "⭐ ${it.price}",   // ios: Views/TownPanel.swift:361
                style = appFont(10f, FontWeight.Bold), color = if (wearing) teal else brown.copy(alpha = if (it.lock) 0.5f else 0.9f))   // ios: Views/TownPanel.swift:362
        }
    }

    val n = look.value ?: book.note(res.id)   // ios: Views/TownPanel.swift:247,248
    val lv = TownBond.level(TownBond.points(res.id))   // ios: Views/TownPanel.swift:249
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/TownPanel.swift:250
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:251
            for (k in TownDressPanel.tabs) {   // ios: Views/TownPanel.swift:252
                Box(Modifier
                    .plainClickable { kind = k }   // ios: Views/TownPanel.swift:253,258 Button · buttonStyle(.plain)
                    .defaultMinSize(minHeight = 30.dp).background(if (kind == k) brown else Color.Transparent, CapsuleShape).padding(horizontal = 10.dp),   // ios: Views/TownPanel.swift:255,256 padding(.horizontal, 10).frame(minHeight: 30)
                    contentAlignment = Alignment.Center) {
                    T(TownDressPanel.kinds[k], style = appFont(13f, FontWeight.Bold), color = if (kind == k) Color.White else brown)   // ios: Views/TownPanel.swift:254
                }
            }
            Spacer(Modifier.weight(1f).widthIn(min = 0.dp))   // ios: Views/TownPanel.swift:260 Spacer(minLength: 0)
            Box(Modifier.defaultMinSize(minHeight = 32.dp).plainClickable { name = true }, contentAlignment = Alignment.Center) {   // ios: Views/TownPanel.swift:261 Button("이름") · frame(minHeight: 32)
                T("이름", style = appFont(12f, FontWeight.Bold), color = brown)
            }
            T("⭐ ${book.candy}", style = appFont(13f, FontWeight.Bold), color = ink)   // ios: Views/TownPanel.swift:262
        }
        if (kind == 4) {   // ios: Views/TownPanel.swift:264 작은 화면에선 칸 안에서 밀어 본다 (ScrollView showsIndicators: false)
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { parts(n) }
        } else {   // ios: Views/TownPanel.swift:265
            val list = items(kind, lv)
            VGrid(count = 3, hSpacing = 6.dp, vSpacing = 6.dp, n = list.size) { i, mod -> cell(list[i], n, mod) }   // ios: Views/TownPanel.swift:266,267 LazyVGrid 세 칸 · ForEach(items(kind, lv))
            Spacer(Modifier.weight(1f).heightIn(min = 0.dp))   // ios: Views/TownPanel.swift:270 Spacer(minLength: 0)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/TownPanel.swift:271
            Box(Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)   // ios: Views/TownPanel.swift:274
                .strokeR(brown, 8.dp, 2.dp)   // ios: Views/TownPanel.swift:275
                .plainClickable { revert(); line.value = "원래대로 돌아왔어요." },   // ios: Views/TownPanel.swift:272,277 Button · buttonStyle(.plain)
                contentAlignment = Alignment.Center) {
                T("되돌리기", style = appFont(14f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:273
            }
            Box(Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)   // ios: Views/TownPanel.swift:280
                .background(brown, RoundedCornerShape(8.dp))   // ios: Views/TownPanel.swift:281
                .plainClickable { wear(n) },   // ios: Views/TownPanel.swift:278,283 Button · buttonStyle(.plain)
                contentAlignment = Alignment.Center) {
                T("이대로 입기", style = appFont(14f, FontWeight.Bold), color = Color.White)   // ios: Views/TownPanel.swift:279
            }
        }
    }
    DisposableEffect(Unit) { onDispose { revert() } }      // ios: Views/TownPanel.swift:286 입지 않고 나가면 미리 보기는 버린다
    Sheet(show = name, onDismiss = { name = false }) { TownDress(res = res, dismiss = { name = false }) }   // ios: Views/TownPanel.swift:287
}

/** 꾸미기 칸의 작은 그림: 모자·소품 도트를 칸 크기에 맞춰 그린다 (그림 파일 없이) */
// ios: Views/TownPanel.swift:402
@Composable
private fun TownItemIcon(
    px: List<Px>,   // ios: Views/TownPanel.swift:403
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {   // ios: Views/TownPanel.swift:404,405
        if (px.isEmpty()) return@Canvas   // ios: Views/TownPanel.swift:406
        val x0 = px.minOf { (x) -> x }; val y0 = px.minOf { (_, y) -> y }   // ios: Views/TownPanel.swift:407
        val w = px.maxOf { (x, _, pw) -> x + pw } - x0; val h = px.maxOf { (_, y, _, ph) -> y + ph } - y0   // ios: Views/TownPanel.swift:408
        val u = min(size.width / w.toFloat(), size.height / h.toFloat())   // ios: Views/TownPanel.swift:409
        val ox = (size.width - w * u) / 2; val oy = (size.height - h * u) / 2   // ios: Views/TownPanel.swift:410
        for (r in px) {   // ios: Views/TownPanel.swift:411
            val (rx, ry, rw, rh, rc) = r
            val v = rc.toLong()   // ios: Views/TownPanel.swift:412
            drawRect(Color(red = (v shr 16 and 0xFF) / 255f, green = (v shr 8 and 0xFF) / 255f, blue = (v and 0xFF) / 255f),   // ios: Views/TownPanel.swift:414
                Offset(ox + (rx - x0) * u, oy + (ry - y0) * u), Size(rw * u, rh * u))   // ios: Views/TownPanel.swift:413
        }
    }
}

// ── 이 파일 안 도움 (Swift 에 따로 없는 안드로이드 자리) ──

/** LazyVGrid(columns: count 개 .flexible): 칸 수가 적어 줄로 나눠 그린다. cell 은 칸 폭을 채울 Modifier 를 받는다 */
@Composable
private fun VGrid(count: Int, hSpacing: Dp, vSpacing: Dp, n: Int, cell: @Composable (Int, Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(vSpacing)) {
        for (row in 0 until (n + count - 1) / count) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(hSpacing), verticalAlignment = Alignment.CenterVertically) {
                for (c in 0 until count) {
                    val i = row * count + c
                    if (i < n) cell(i, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** .overlay(RoundedRectangle(cornerRadius: r).stroke(색, lineWidth: w)): SwiftUI 처럼 선 가운데가 테두리에 걸친다 (Compose border 는 안쪽).
 *  r 이 null 이면 Capsule/Circle (높이의 절반). dash = StrokeStyle(dash:) */
private fun Modifier.strokeR(c: Color, r: Dp?, width: Dp = 1.dp, dash: FloatArray? = null): Modifier = this.drawWithContent {
    drawContent()
    val rr = r?.toPx() ?: (min(size.width, size.height) / 2)
    drawRoundRect(c, Offset.Zero, Size(size.width, size.height), CornerRadius(rr, rr),
        style = Stroke(width.toPx(), pathEffect = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { d[it] * density }) }))
}

/** @AppStorage(키) Int 자리. 같은 키는 앱 전체에서 한 상태를 같이 써서 (iOS AppStorage 처럼) 다른 화면에도 바로 보인다.
 *  동네 배경("town.bg")은 TownView 쪽도 이것으로 읽어야 꾸미기 미리 보기가 바로 보인다 */
private val prefInts = HashMap<String, MutableState<Int>>()

fun townPrefInt(key: String, default: Int): MutableState<Int> = synchronized(prefInts) {
    prefInts.getOrPut(key) {
        val s = mutableIntStateOf(Defaults.prefs.getInt(key, default))
        object : MutableState<Int> by s {
            override var value: Int
                get() = s.intValue
                set(v) { s.intValue = v; Defaults.prefs.edit().putInt(key, v).apply() }
        }
    }
}
