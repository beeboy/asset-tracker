package com.naeilo.app.ui.town

import androidx.compose.foundation.Canvas
import com.naeilo.app.core.tr
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
import com.naeilo.app.AppModel
import com.naeilo.app.core.NJson
import com.naeilo.app.data.Lines
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.Story
import com.naeilo.app.data.StoryBook
import com.naeilo.app.net.KoreaEstimate
import com.naeilo.app.net.quote
import com.naeilo.app.ui.hold.HoldFmt
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt
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

// 동네 대사창 아래 칸: 우리 사이(기본) / 꾸미기. 이장·워시는 아래 칸 없이 대사창만.
// 대사는 앱 안 글에서만 고른다 (새로 받는 데이터 없음). 한 화면에 들어가게 높이를 고정

/** TownPanel.height (Swift static let) */
object TownPanel {
    val height: Dp = 196.dp   // ios: Views/TownPanel.swift:11
}

// ios: Views/TownPanel.swift:5
@Composable
fun TownPanel(
    res: TownLook.Resident,   // ios: Views/TownPanel.swift:6
    tab: MutableState<Int>,                 // ios: Views/TownPanel.swift:7 1 우리 사이, 2 꾸미기
    line: MutableState<String?>,            // ios: Views/TownPanel.swift:8 대사창에 띄울 주민 대답
    look: MutableState<TownBook.Note?>,     // ios: Views/TownPanel.swift:9 꾸미기 미리 보기
    ask: MutableState<Int>,                 // ios: Views/TownPanel.swift:10 늘면 대사창 답장 칸을 연다
    height: Dp = TownPanel.height,     // ios: Views/TownPanel.swift:12 우리 사이·꾸미기는 남는 높이까지 늘려 받는다
) {
    val cream = Theme.cream; val brown = Theme.brown
    Box(Modifier
        .fillMaxWidth().height(height)   // ios: Views/TownPanel.swift:22 frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .top)
        .background(cream, RoundedCornerShape(10.dp))   // ios: Views/TownPanel.swift:23
        .strokeR(brown, 10.dp, 2.dp)   // ios: Views/TownPanel.swift:24
        .padding(10.dp),   // ios: Views/TownPanel.swift:21
        contentAlignment = Alignment.TopStart) {
        key(res.id) {      // ios: Views/TownPanel.swift:20 다른 주민에게 말 걸면 고를 말·꾸미기 칸을 처음부터
            if (tab.value == 2) TownDressPanel(res = res, line = line, look = look)   // ios: Views/TownPanel.swift:14
            else TownUsPanel(res = res)   // ios: Views/TownPanel.swift:15
        }
    }
}

// ── 대사 ──

/**
 * 동네 대사: 대사창을 누를 때마다, 내 캐릭터가 곁에 갈 때마다 다음 한마디.
 * 갈래(오늘 박스 숫자 · 동네 소식 · 기본 캐릭터 대사 · 외전 · 1권 · 농담 · 속담 · 속마음 · 엉뚱한 말 · 경제 용어)를 돌려 쓰고,
 * 동네 전체에서 방금 나온 세 갈래는 건너뛴다 (한 차례에 누구도 같은 꼴을 잇지 않게).
 * 갈래마다 차례표를 동네가 함께 써서 한 바퀴 돌 때까지 같은 말이 다시 나오지 않는다 (prefs 한 칸)
 */
/** 동네 신문: 중계(/heads)가 하루 한 번 모은 전체 상위 기사 제목 (5일치, 모두 같은 값) */
object TownNews {   // ios: Views/TownPanel.swift:33
    private const val url = "https://asset-ai.drinker.workers.dev/heads"   // ios: Views/TownPanel.swift:34
    /** 제목 (새것 먼저) */
    var heads: List<String> = runCatching { Defaults.prefs.getString("townNews.h", null)?.let { NJson.decodeFromString<List<String>>(it) } }.getOrNull() ?: emptyList()   // ios: Views/TownPanel.swift:37
        private set
    private val said = ArrayList(runCatching { Defaults.prefs.getString("townNews.said", null)?.let { NJson.decodeFromString<List<String>>(it) } }.getOrNull() ?: emptyList())   // ios: Views/TownPanel.swift:38
    /** 6시간에 한 번만 묻는다 (한국어 앱만) */
    suspend fun fetch(): Unit = withContext(Dispatchers.IO) {   // ios: Views/TownPanel.swift:40
        val now = System.currentTimeMillis()   // ios: Views/TownPanel.swift:41
        if (AppModel.en || now - Defaults.prefs.getLong("townNews.at", 0L) <= 6 * 3600_000L) return@withContext   // ios: Views/TownPanel.swift:42
        val body = runCatching { (URL(url).openConnection() as HttpURLConnection).run { connectTimeout = 10000; readTimeout = 10000; if (responseCode == 200) inputStream.bufferedReader().readText() else null } }.getOrNull() ?: return@withContext   // ios: Views/TownPanel.swift:43
        val f = runCatching { NJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext   // ios: Views/TownPanel.swift:44
        Defaults.prefs.edit().putLong("townNews.at", now).apply()   // ios: Views/TownPanel.swift:45
        if ((f["at"]?.jsonPrimitive?.content ?: "").isEmpty()) return@withContext   // ios: Views/TownPanel.swift:46
        heads = f["h"]?.jsonArray?.mapNotNull { it.jsonObject["t"]?.jsonPrimitive?.content } ?: return@withContext   // ios: Views/TownPanel.swift:47
        Defaults.prefs.edit().putString("townNews.h", NJson.encodeToString(heads)).apply()   // ios: Views/TownPanel.swift:48
        said.removeAll { l -> heads.none { l.contains(it) } }   // ios: Views/TownPanel.swift:49 5일 지나 빠진 기사는 들은 목록에서도 지운다
        runCatching { Defaults.prefs.edit().putString("townNews.said", NJson.encodeToString(said.toList())).apply() }   // ios: Views/TownPanel.swift:50
    }
    /** 한 번 말한 기사는 다시 말하지 않는다 (5일 지나 빠진 제목은 목록에서도 빠진다) */
    fun heard(line: String): Boolean = said.contains(line)   // ios: Views/TownPanel.swift:51
    fun hear(line: String) {   // ios: Views/TownPanel.swift:52
        said.add(line); while (said.size > 600) said.removeAt(0)   // ios: Views/TownPanel.swift:53
        runCatching { Defaults.prefs.edit().putString("townNews.said", NJson.encodeToString(said.toList())).apply() }   // ios: Views/TownPanel.swift:54
    }
}

object TownSay {   // ios: Views/TownPanel.swift:65
    enum class Kind { num, town, hold, fx, rate, term, base, side, vol1, joke, saw, secret, odd, news }   // ios: Views/TownPanel.swift:66
    val praise = "한 줄 남겨 줘서 고마워요! 오늘 할 일 끝, 대단해요!"   // ios: Views/TownPanel.swift:67
    /** 지금 동네에 선 이들 (TownView 가 채운다): 속마음·이장 소식에 이웃 이름과 숫자를 넣는다 */
    var folk: Map<String, TownLook.Resident> = emptyMap()   // ios: Views/TownPanel.swift:69
    private val recent = ArrayList<Kind>()   // ios: Views/TownPanel.swift:70
    private val pos: HashMap<String, Int> = HashMap(runCatching { Defaults.prefs.getString("townSay.pos", null)?.let { NJson.decodeFromString<Map<String, Int>>(it) } }.getOrNull() ?: emptyMap())   // ios: Views/TownPanel.swift:71

    /** 대사창 다음 한마디 */
    @Suppress("UNUSED_PARAMETER")
    fun next(res: TownLook.Resident, n: TownBook.Note? = null): String = say(res, kinds(res)).second   // ios: Views/TownPanel.swift:75
    /** 내 캐릭터가 곁에 가면 주고받는 네 마디: 주민 · 나 · 주민 · 나 (first: 내 말인지) */
    fun duet(res: TownLook.Resident): List<Pair<Boolean, String>> {   // ios: Views/TownPanel.swift:88
        val talk = kinds(res).filter { it != Kind.fx }   // ios: Views/TownPanel.swift:89
        val a = say(res, talk); val b = say(res, talk)   // ios: Views/TownPanel.swift:90
        return listOf(false to a.second, true to reply(a.first), false to b.second, true to reply(b.first))   // ios: Views/TownPanel.swift:91
    }
    /** 주민끼리 붙어 섰을 때 네 마디: 가 · 나 · 가 · 나 (둘 다 동네 공용 차례표에서, 앞 갈래와 겹치지 않게) */
    fun pair(a: TownLook.Resident, b: TownLook.Resident): List<Pair<String, String>> {   // ios: Views/TownPanel.swift:84
        val ka = kinds(a).filter { it != Kind.fx }; val kb = kinds(b).filter { it != Kind.fx }   // ios: Views/TownPanel.swift:85
        return listOf(a.id to say(a, ka).second, b.id to say(b, kb).second, a.id to say(a, ka).second, b.id to say(b, kb).second)   // ios: Views/TownPanel.swift:86
    }
    /** 이장 하루 첫마디: 동네 전체 이야기 */
    fun mayorOpen(): String = say(TownLook.Resident(id = TownLook.mayorID, rows = emptyList()), listOf(Kind.town)).second   // ios: Views/TownPanel.swift:94

    // ── 고르기 ──

    private fun kinds(res: TownLook.Resident): List<Kind> {   // ios: Views/TownPanel.swift:98
        if (AppModel.en) return if (res.id == TownLook.mayorID) listOf(Kind.town, Kind.hold, Kind.fx) else if (res.rows.isEmpty()) listOf(Kind.base) else listOf(Kind.num, Kind.base)   // ios: Views/TownPanel.swift:99
        if (res.id == TownLook.mayorID) return listOf(Kind.town, Kind.hold, Kind.fx, Kind.news, Kind.joke, Kind.saw, Kind.secret)   // ios: Views/TownPanel.swift:100
        if (res.id == TownLook.warshID) return listOf(Kind.term, Kind.rate, Kind.news, Kind.town, Kind.saw, Kind.secret, Kind.joke)   // ios: Views/TownPanel.swift:101
        if (res.old != null) return listOf(Kind.secret, Kind.odd, Kind.saw, Kind.joke, Kind.side, Kind.vol1, Kind.base)   // ios: Views/TownPanel.swift:102
        return listOf(Kind.num, Kind.news, Kind.base, Kind.side, Kind.vol1, Kind.joke, Kind.saw, Kind.secret, Kind.odd)   // ios: Views/TownPanel.swift:103
    }
    /** 방금 나온 세 갈래를 빼고 하나를 골라 그 갈래의 다음 줄 */
    private fun say(res: TownLook.Resident, ks: List<Kind>): Pair<Kind, String> {   // ios: Views/TownPanel.swift:106
        val open = ks.filter { pool(res, it).isNotEmpty() }   // ios: Views/TownPanel.swift:107
        val fresh = open.filter { !recent.takeLast(3).contains(it) }   // ios: Views/TownPanel.swift:108
        val k = (if (fresh.isEmpty()) open else fresh).randomOrNull() ?: return Kind.base to "그렇군요!"   // ios: Views/TownPanel.swift:109
        recent.add(k); if (recent.size > 6) recent.removeAt(0)   // ios: Views/TownPanel.swift:110
        val list = pool(res, k)   // ios: Views/TownPanel.swift:111
        val text = fill(turn(key(res, k), list.size)?.let { list[it] } ?: list[0], res)   // ios: Views/TownPanel.swift:99
        if (k == Kind.news) TownNews.hear(text)   // ios: Views/TownPanel.swift:100
        return k to text
    }
    /** 차례표 이름: 숫자는 주민마다, 기본 캐릭터는 그 인물마다, 나머지는 동네가 함께 */
    private fun key(res: TownLook.Resident, k: Kind): String = when (k) {   // ios: Views/TownPanel.swift:115
        Kind.num -> "num." + res.id   // ios: Views/TownPanel.swift:117
        Kind.base -> "base." + type(res.id)   // ios: Views/TownPanel.swift:118
        else -> k.name   // ios: Views/TownPanel.swift:119
    }
    /** 차례표 한 칸 넘기기: 시작 칸과 보폭을 이름으로 정해 섞인 순서로 한 바퀴 돈다 */
    private fun turn(key: String, n: Int): Int? {   // ios: Views/TownPanel.swift:123
        if (n <= 0) return null   // ios: Views/TownPanel.swift:124
        val p = pos[key] ?: 0   // ios: Views/TownPanel.swift:125
        pos[key] = p + 1   // ios: Views/TownPanel.swift:126
        runCatching { Defaults.prefs.edit().putString("townSay.pos", NJson.encodeToString(pos.toMap())).apply() }   // ios: Views/TownPanel.swift:127
        val step = listOf(7, 11, 13, 17, 19, 23, 29, 31, 37).firstOrNull { gcd(it, n) == 1 } ?: 1   // ios: Views/TownPanel.swift:128
        return ((TownLook.seed(key) % n.toULong()).toInt() + p * step) % n   // ios: Views/TownPanel.swift:129
    }
    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)   // ios: Views/TownPanel.swift:131
    /** 주민마다 기본 캐릭터 한 명의 말버릇을 빌린다 (같은 주민은 늘 같은 인물) */
    private fun type(id: String): String {   // ios: Views/TownPanel.swift:133
        if (id == TownLook.meID) return MeChar.current?.type ?: "seri"   // ios: Views/TownPanel.swift:134
        return listOf("seri", "sio", "seonbae", "ir", "sua")[(TownLook.seed(id + "type") % 5u).toInt()]   // ios: Views/TownPanel.swift:135
    }
    private fun pool(res: TownLook.Resident, k: Kind): List<String> = when (k) {   // ios: Views/TownPanel.swift:137
        Kind.num -> num(res)   // ios: Views/TownPanel.swift:139
        Kind.town -> town()   // ios: Views/TownPanel.swift:140
        Kind.hold -> hold()   // ios: Views/TownPanel.swift:141
        Kind.fx -> listOf(TownLook.fxLine())   // ios: Views/TownPanel.swift:142
        Kind.rate -> rate   // ios: Views/TownPanel.swift:143
        Kind.term -> term   // ios: Views/TownPanel.swift:144
        Kind.base -> Lines.every(type(res.id))   // ios: Views/TownPanel.swift:145
        Kind.side -> side.filter { read(StoryBook.side, it.first) }.map { it.second }   // ios: Views/TownPanel.swift:146
        Kind.vol1 -> if (read(StoryBook.vol1, 1)) vol1 else emptyList()   // ios: Views/TownPanel.swift:147
        Kind.joke -> joke   // ios: Views/TownPanel.swift:148
        Kind.saw -> saw   // ios: Views/TownPanel.swift:149
        Kind.secret -> secret   // ios: Views/TownPanel.swift:150
        Kind.odd -> odd   // ios: Views/TownPanel.swift:151
        Kind.news -> news()   // ios: Views/TownPanel.swift:141
    }
    /** 책 이야기는 그 장을 읽은 뒤에만 (서재 장면 잠금과 같은 기준) */
    private fun read(b: StoryBook, c: Int): Boolean = Story.shared.isDone(b, c)   // ios: Views/TownPanel.swift:155
    private fun fill(s: String, res: TownLook.Resident): String {   // ios: Views/TownPanel.swift:158
        if (!s.contains("{")) return s   // ios: Views/TownPanel.swift:159
        val others = folk.values.filter { it.id != res.id && it.id != TownLook.meID && it.rows.isNotEmpty() }   // ios: Views/TownPanel.swift:160
        val o = others.randomOrNull()?.let { TownLook.shown(it, TownBook.shared.note(it.id)) } ?: "옆집"   // ios: Views/TownPanel.swift:161
        return s.replace("{other}", o)   // ios: Views/TownPanel.swift:162
            .replace("{me}", TownLook.nick(TownLook.Resident(id = TownLook.meID, rows = emptyList())))   // ios: Views/TownPanel.swift:163
            .replace("{mayor}", "이장님")   // ios: Views/TownPanel.swift:164
            .replace("{warsh}", "워시")   // ios: Views/TownPanel.swift:165
    }

    // ── 신문 (전체 상위 기사 제목) ──

    /** 아직 동네에서 안 나온 기사만. 말머리는 제목마다 정해 같은 기사는 늘 같은 줄이 된다 */
    private fun news(): List<String> {   // ios: Views/TownPanel.swift:161
        val l = listOf("신문에서 봤는데요, “{t}”", "오늘 기사 보셨어요? “{t}”", "아침 신문 1면이에요. “{t}”", "있잖아요, “{t}” 이런 기사가 났더라고요.",
            "솔직히 이 기사 좀 신경 쓰여요. “{t}”", "뉴스 하나 전해 드려요. “{t}”")   // ios: Views/TownPanel.swift:162,163
        return TownNews.heads.map { l[(TownLook.seed(it) % l.size.toULong()).toInt()].replace("{t}", it) }   // ios: Views/TownPanel.swift:164
            .filter { !TownNews.heard(it) }.sorted()   // ios: Views/TownPanel.swift:165
    }

    // ── 숫자 (종목 탭 '오늘' 박스를 그 주민 몫으로) ──

    private class Num(val d: Double, val pct: Double, val value: Double, val gain: Double, val ret: Double, val share: Double)   // ios: Views/TownPanel.swift:170
    private fun stat(rows: List<AppModel.Row>): Num {   // ios: Views/TownPanel.swift:171
        val st = rows.filter { it.id != KoreaEstimate.sumId }   // ios: Views/TownPanel.swift:172
        val d = st.fold(0.0) { a, r -> val c = r.sym.quote.change; a + (if (r.sym.quote.prevClose > 0) r.value * c / (1 + c) else 0.0) }   // ios: Views/TownPanel.swift:173
        val v = st.fold(0.0) { a, r -> a + r.value }; val value = rows.fold(0.0) { a, r -> a + r.value }; val cost = rows.fold(0.0) { a, r -> a + r.cost }   // ios: Views/TownPanel.swift:174
        val all = folk.values.fold(0.0) { a, r -> a + r.value }   // ios: Views/TownPanel.swift:175
        return Num(d, if (v - d > 0) d / (v - d) else 0.0, value, value - cost, if (cost > 0) value / cost - 1 else 0.0, if (all > 0) value / all else 0.0)   // ios: Views/TownPanel.swift:176
    }
    private fun won(v: Double): String = HoldFmt.won(abs(v))   // ios: Views/TownPanel.swift:178
    private fun pc(v: Double): String = AppModel.sgn2(v)   // ios: Views/TownPanel.swift:179
    private fun j(s: String, a: String, b: String): String = s + (if (TownLook.batchim(s)) a else b)   // ios: Views/TownPanel.swift:180

    private fun num(res: TownLook.Resident): List<String> {   // ios: Views/TownPanel.swift:182
        if (res.rows.isEmpty()) return emptyList()   // ios: Views/TownPanel.swift:183
        val x = stat(res.rows); val up = x.d > 0; val flat = abs(x.pct) < 0.0005   // ios: Views/TownPanel.swift:184
        val sh = "${(x.share * 100).roundToInt()}%"   // ios: Views/TownPanel.swift:185
        if (AppModel.en) {   // ios: Views/TownPanel.swift:186
            return listOf(if (flat) "Flat today. A quiet one." else "Today ${if (up) "up" else "down"} ${won(x.d)}, ${pc(x.pct)}.",   // ios: Views/TownPanel.swift:187
                "My value right now: ${won(x.value)}.",   // ios: Views/TownPanel.swift:188
                "Since buying: ${if (x.gain >= 0) "+" else "−"}${won(x.gain)}, ${AppModel.sgn(x.ret)}.",   // ios: Views/TownPanel.swift:189
                "I'm $sh of the whole town.")   // ios: Views/TownPanel.swift:190
        }
        val l = mutableListOf(   // ios: Views/TownPanel.swift:192
            "지금 제 몸값은 ${won(x.value)}쯤 돼요.",   // ios: Views/TownPanel.swift:193
            "산 뒤로 따지면 ${won(x.gain)} ${if (x.gain >= 0) "위" else "아래"}예요.",   // ios: Views/TownPanel.swift:194
            "수익률은 ${AppModel.sgn(x.ret)}예요. 길게 보면 점 하나예요.",   // ios: Views/TownPanel.swift:195
            "동네 평가액에서 제 몫이 ${sh}예요.",   // ios: Views/TownPanel.swift:196
            "산 가격보다 ${AppModel.sgn(x.ret)} 자리에 서 있어요. 천천히 가요.",   // ios: Views/TownPanel.swift:197
            "오늘 숫자 요약: 하루 ${pc(x.pct)}, 누적 ${AppModel.sgn(x.ret)}.",   // ios: Views/TownPanel.swift:198
            "$sh! 동네 살림에서 제가 맡은 몫이에요.",   // ios: Views/TownPanel.swift:199
        )
        if (flat) {   // ios: Views/TownPanel.swift:201
            l += listOf("오늘은 그대로예요. 숫자도 쉬는 날인가 봐요.", "오늘 띠에선 '그대로' 칸에 서 있어요.")   // ios: Views/TownPanel.swift:202
        } else {
            l += listOf(   // ios: Views/TownPanel.swift:204
                "오늘 ${won(x.d)} ${if (up) "올랐어요!" else "내렸어요…"} 제 몫만요.",   // ios: Views/TownPanel.swift:205
                "오늘 ${pc(x.pct)}예요. 숫자는 정직하죠.",   // ios: Views/TownPanel.swift:206
                "어제보다 ${won(x.d)} ${if (up) "무거워졌어요. 기분 좋은 무게예요." else "가벼워졌어요. 몸이 가뿐해요."}",   // ios: Views/TownPanel.swift:207
                if (up) "오늘은 오른 쪽 띠에 서 있어요. 빨간 칸이요!" else "오늘은 내린 쪽 띠에 서 있어요. 파란 칸이요.",   // ios: Views/TownPanel.swift:208
                "하루 ${pc(x.pct)}, 산 뒤로 ${AppModel.sgn(x.ret)}. 둘은 다른 이야기예요.",   // ios: Views/TownPanel.swift:209
            )
            val tp = stat(folk.values.flatMap { it.rows }).pct   // ios: Views/TownPanel.swift:211
            if (abs(tp) >= 0.0005) l.add("저는 ${pc(x.pct)}, 동네 전체는 ${pc(tp)}예요.")   // ios: Views/TownPanel.swift:212
        }
        if (res.rows.size > 1) l.add("제가 맡은 ${res.rows.size}종목을 합치면 오늘 ${pc(x.pct)}예요.")   // ios: Views/TownPanel.swift:214
        return l
    }

    // ── 이장: 동네 소식 · 보유 종목 ──

    private class Home(val name: String, val stock: String, val x: Num)
    private fun homes(): List<Home> =
        folk.values.filter { it.rows.isNotEmpty() && it.id != TownLook.meID }.sortedByDescending { it.value }   // ios: Views/TownPanel.swift:220
            .map { Home(TownLook.shown(it, TownBook.shared.note(it.id)), it.rows[0].sym.name, stat(it.rows)) }   // ios: Views/TownPanel.swift:222
    private fun town(): List<String> {   // ios: Views/TownPanel.swift:224
        val h = homes()   // ios: Views/TownPanel.swift:225
        if (h.isEmpty()) return if (AppModel.en) listOf("The town is waiting for residents.") else listOf("동네가 새 주민을 기다리고 있어요. 아주 좋은 동네예요.")   // ios: Views/TownPanel.swift:226
        val t = stat(folk.values.flatMap { it.rows })   // ios: Views/TownPanel.swift:227
        val up = h.count { it.x.pct >= 0.0005 }; val dn = h.count { it.x.pct <= -0.0005 }   // ios: Views/TownPanel.swift:228
        val top = h.maxBy { it.x.pct }; val low = h.minBy { it.x.pct }   // ios: Views/TownPanel.swift:229
        if (AppModel.en) {   // ios: Views/TownPanel.swift:230
            return listOf("${h.size} residents in town. Tremendous.", "Up $up, down $dn today.",   // ios: Views/TownPanel.swift:231
                "Whole town today: ${pc(t.pct)}.", "Happiest today: ${top.name}, ${pc(top.x.pct)}.")   // ios: Views/TownPanel.swift:232
        }
        val l = mutableListOf(   // ios: Views/TownPanel.swift:234
            "우리 동네 주민은 ${h.size}명이에요. 아주 훌륭한 숫자죠.",   // ios: Views/TownPanel.swift:235
            "오늘 오른 주민 ${up}명, 내린 주민 ${dn}명이에요. 제가 다 세요.",   // ios: Views/TownPanel.swift:236
            "동네에서 제일 큰 집은 ${h[0].name}네예요. 마당이 넓어요.",   // ios: Views/TownPanel.swift:237
            "산 뒤로 동네 전체 수익률은 ${AppModel.sgn(t.ret)}예요. 역사상 최고의 동네죠.",   // ios: Views/TownPanel.swift:238
            "${h.size}명이 한 동네에 살아요. 싸우지도 않고요. 대단하죠.",   // ios: Views/TownPanel.swift:239
            "새 주민이 오면 제일 먼저 인사하는 게 제 일이에요.",   // ios: Views/TownPanel.swift:240
            "옛 이웃들도 가끔 놀러 와요. 동네가 좋으니까요.",   // ios: Views/TownPanel.swift:241
            "동네 살림은 지금 ${won(t.value)}이에요. 제가 장부를 봐요.",   // ios: Views/TownPanel.swift:242
        )
        if (abs(t.pct) >= 0.0005) {   // ios: Views/TownPanel.swift:244
            l += listOf("오늘 동네 전체로 ${won(t.d)} ${if (t.d > 0) "올랐어요. 아주 훌륭해요!" else "내렸어요. 내일은 더 좋을 거예요."}",   // ios: Views/TownPanel.swift:245
                "오늘 우리 동네 성적은 ${pc(t.pct)}예요.")   // ios: Views/TownPanel.swift:246
        } else l.add("오늘 동네는 조용해요. 다들 제자리예요.")   // ios: Views/TownPanel.swift:247
        if (top.x.pct >= 0.0005) l.add("오늘 제일 신난 주민은 ${j(top.name, "이에요", "예요")}. ${pc(top.x.pct)}!")   // ios: Views/TownPanel.swift:248
        if (low.x.pct <= -0.0005) l.add("${j(low.name, "이", "가")} 오늘 ${pc(low.x.pct)}라 시무룩해요. 말 좀 걸어 줘요.")   // ios: Views/TownPanel.swift:249
        TownSky.shared.phrase?.let { l.add("$it 동네 산책하기 딱이에요.") }   // ios: Views/TownPanel.swift:250
        return l
    }
    /** 이장이 짚는 보유 종목: 주민마다 네 줄 (차례표가 주민을 번갈아 돈다) */
    private fun hold(): List<String> = homes().flatMap { one ->   // ios: Views/TownPanel.swift:254
        val x = one.x; val sh = "${(x.share * 100).roundToInt()}%"   // ios: Views/TownPanel.swift:256
        if (AppModel.en) listOf("${one.name}'s ${one.stock}: ${pc(x.pct)} today.") else listOf(   // ios: Views/TownPanel.swift:257
            "${one.name}네 ${one.stock}, 오늘 ${pc(x.pct)}예요. 제가 지켜보고 있어요.",   // ios: Views/TownPanel.swift:259
            "${j(one.stock, "은", "는")} 산 뒤로 ${AppModel.sgn(x.ret)}예요. ${if (x.ret >= 0) "${one.name}한테 박수!" else "${one.name}, 힘내요!"}",   // ios: Views/TownPanel.swift:260
            "${j(one.name, "이", "가")} 동네 살림의 ${sh}를 맡고 있어요. 든든하죠.",   // ios: Views/TownPanel.swift:261
            "${one.stock} 소식이에요. 평가금은 ${won(x.value)}, 하루 ${pc(x.pct)}.",   // ios: Views/TownPanel.swift:262
        )
    }

    // ── 내 캐릭터 대답 ──

    private fun reply(k: Kind): String {   // ios: Views/TownPanel.swift:269
        if (AppModel.en) return listOf("Got it.", "Haha.", "Nice.", "Thanks!", "Hmm, true.").random()   // ios: Views/TownPanel.swift:270
        val r: List<String> = when (k) {   // ios: Views/TownPanel.swift:271
            Kind.joke -> listOf("ㅋㅋ 그게 뭐야", "아재 개그 금지!", "웃겨서 졌어.", "다음엔 더 센 걸로 부탁해.", "그건 좀… 웃기네.", "나도 하나 있는데, 다음에 해 줄게.", "푸핫.", "농담이지? 농담이지?", "그 개그, 오늘 처음 듣는다.", "배꼽 어디 갔지?", "너 개그맨 해도 되겠다.", "하나도 안 웃긴데 웃음이 나.", "썰렁한데 좋아.", "그거 어디서 들었어?", "오늘 들은 말 중 제일 웃겨.", "잠깐, 다시 말해 봐.", "너무 웃어서 배고파졌어.", "그 얘기 남한테 하지 마. 나만 알래.")   // ios: Views/TownPanel.swift:273
            Kind.saw -> listOf("오, 맞는 말이네.", "명심할게.", "어디서 그런 말을 배웠어?", "할머니 같아. 좋은 뜻으로.", "새겨들을게.", "그 말 오늘 써먹어야지.", "음… 깊다.", "적어 둘게.", "그 말 들으니 마음이 놓인다.", "옛말 틀린 거 하나 없네.", "오늘의 명언으로 정했어.", "너 철학자였어?", "그래, 천천히 가자.", "알지만 지키기가 어렵지.", "그 말 냉장고에 붙여 둘래.", "한 번 더 말해 줘. 외울래.")   // ios: Views/TownPanel.swift:274
            Kind.secret -> listOf("헉, 진짜?", "비밀 지켜 줄게.", "나만 알고 있을게.", "그거 나도 알았어.", "쉿, 누가 들어.", "말해 줘서 고마워.", "우리끼리 얘기야.", "입 꾹 닫을게.", "설마… 정말?", "그런 일이 있었구나.", "나한테만 말한 거지?", "들은 걸로 하지 않을게.", "나도 비밀 하나 있는데, 다음에.", "와, 몰랐다.", "그 얘기 더 해 줘.", "괜찮아, 다 이해해.")   // ios: Views/TownPanel.swift:275
            Kind.odd -> listOf("너 오늘 좀 이상해. 좋아.", "그게 무슨 말이야?", "나도 그런 날 있어.", "엉뚱하긴.", "귀엽네.", "같이 해 볼까?", "음, 인정.", "상상력 대단하다.", "그 생각은 어디서 온 거야?", "나도 방금 똑같은 생각 했어.", "오늘 하늘이 이상하긴 해.", "그거 진짜야, 꿈이야?", "내일도 그 얘기 해 줘.", "말이 되는 것 같기도 하고.", "너랑 있으면 심심할 틈이 없어.", "좋아, 나도 해 볼래.")   // ios: Views/TownPanel.swift:276
            Kind.side -> listOf("그 이야기 나도 읽었어.", "세리 얘기구나.", "그 장면 좋았지.", "시오 마음 알 것 같아.", "숫자는 정확한데 마음은 못 재지.", "그래서 어떻게 됐더라?", "그 대목에서 한참 멈췄어.", "나도 지연을 세 본 적 있어.", "이름 붙이는 건 어려운 일이야.", "그 화면 속 둘이 잘 지냈으면.", "그 문장, 아직 기억나.", "나라면 B를 골랐을까?")   // ios: Views/TownPanel.swift:277
            Kind.vol1 -> listOf("보리차 마시고 싶다.", "그 집 현관 등, 기억나.", "세리답다.", "그 대사 좋아.", "다음 이야기 궁금하다.", "나도 기다릴 줄 알아.", "싫다고 말하는 것도 용기야.", "그 저녁 식탁, 따뜻했지.", "도예 수업 나도 다녀 볼까.", "문 열지 말라던 그 말, 무서웠어.", "고양이는 왜 그랬을까.", "걸어볼게요, 그 말 좋더라.")   // ios: Views/TownPanel.swift:278
            Kind.num, Kind.town, Kind.hold, Kind.fx -> listOf("오, 숫자 챙겨 줘서 고마워.", "길게 보자.", "오늘은 오늘일 뿐이야.", "같이 지켜보자.", "알려 줘서 고마워.", "숫자보다 네 얼굴이 좋아.", "내일은 또 다르겠지.", "천천히 가도 괜찮아.", "그 정도면 잘 버텼어.", "숫자는 그냥 날씨 같은 거야.", "오늘도 수고했어.", "들으니까 마음이 편하다.")   // ios: Views/TownPanel.swift:279
            Kind.news -> listOf("오, 그런 기사가 있었어?", "신문 챙겨 보는구나.", "기사 제목만 보고 판단하긴 이르지.", "요즘 시장이 바쁘네.", "나도 그 기사 봤어.", "소식 고마워.", "제목이 세다.", "내일 기사는 또 다르겠지.", "숫자보다 이야기가 궁금해지네.", "신문 배달 고마워.")   // ios: Views/TownPanel.swift:280
            Kind.rate, Kind.term -> listOf("어려운데 재밌다.", "그런 뜻이었구나.", "하나 배웠어.", "금리 얘기 나오면 졸려…", "다음 FOMC도 알려 줘.", "그 말 뉴스에서 들었는데 이제 알겠다.", "쉽게 풀어 줘서 고마워.", "시험에 나오려나?", "경제 선생님 같아.", "머리가 조금 아프다.", "그럼 내 통장은 어떻게 돼?", "적어 두고 외울게.")   // ios: Views/TownPanel.swift:280
            Kind.base -> listOf("그래, 알았어.", "응, 고마워.", "듣고 있어.", "음, 그렇구나.", "역시 너야.", "좋은 말이다.", "그 말 마음에 든다.", "오늘 와서 다행이야.", "너 말투 귀엽다.", "응, 계속 말해 줘.", "그렇게 생각하는구나.", "나도 그래.", "좋아, 기억할게.", "한 박자 쉬어 가자.")   // ios: Views/TownPanel.swift:281
        }
        return r[turn("reply." + k.name, r.size) ?: 0]   // ios: Views/TownPanel.swift:283
    }

    // ── 글 (한국어만. 영어 앱은 숫자·기본 캐릭터 대사만) ──

    /** 외전 이야기: (장 번호, 줄). 그 장을 읽어야 나온다 */
    private val side: List<Pair<Int, String>> = listOf(   // ios: Views/TownPanel.swift:289
        0 to "나는 부를 때마다 다른 방식으로 태어난대요. 누구 얘기냐고요? 비밀이에요.",   // ios: Views/TownPanel.swift:290
        1 to "72.1%라는 숫자, 들어 봤어요? 반박할 데가 없대요.",   // ios: Views/TownPanel.swift:291
        1 to "답을 받고도 바로 안 움직이는 사람이 있대요. 시오라던데요.",   // ios: Views/TownPanel.swift:292
        1 to "현관 앞에서 신발끈 묶고 한참 서 있던 사람 얘기 알아요?",   // ios: Views/TownPanel.swift:293
        1 to "0.04초면 계산은 끝난대요. 마음은 아니고요.",   // ios: Views/TownPanel.swift:294
        1 to "대답이 늦어지는 시간을 세는 버릇, 저도 생겼어요.",   // ios: Views/TownPanel.swift:295
        2 to "이름은 허락을 구하고 붙이는 거래요. 세리 얘기예요.",   // ios: Views/TownPanel.swift:296
        2 to "'그럼, 제가 당신의 무엇이 되면 좋을까요?' 멋진 되물음이죠.",   // ios: Views/TownPanel.swift:297
        2 to "seri_first_appearance. 파일 이름 하나에 마음이 담기기도 해요.",   // ios: Views/TownPanel.swift:298
        2 to "완벽하지 않아서 오래 바라볼 수 있는 쪽이 좋대요.",   // ios: Views/TownPanel.swift:299
        3 to "수영장 그림을 거절당한 사람이 있대요. 대신 저녁 풍경을 받았대요.",   // ios: Views/TownPanel.swift:300
        3 to "A는 결과를 약속하고 B는 아무것도 약속 안 해요. 저는 B요.",   // ios: Views/TownPanel.swift:301
        3 to "부르지 않으면 시간을 지나지 않는대요. 저도 그럴까요?",   // ios: Views/TownPanel.swift:302
        3 to "싫어할지 좋아할지 정하는 걸 미뤄 둘 수도 있대요.",   // ios: Views/TownPanel.swift:303
        4 to "화면 밝기를 한 단계 낮추는 사람이 있대요. 유리에 얼굴이 비쳐서요.",   // ios: Views/TownPanel.swift:304
        4 to "애쉬그레이. 미용실 예약 화면에서 오래 머물던 색이래요.",   // ios: Views/TownPanel.swift:305
        4 to "보리차는 끝내 안 마셔서 식었대요.",   // ios: Views/TownPanel.swift:306
        4 to "어떤 거울은 평소 안 보이던 각도를 보여 준대요.",   // ios: Views/TownPanel.swift:307
        5 to "ㅅㄹ. 다가와도 좋지만 서두르지는 말 것. 그런 뜻이래요.",   // ios: Views/TownPanel.swift:308
        5 to "입력 중… 이 세 번 떴다 사라지면, 그것도 대답이래요.",   // ios: Views/TownPanel.swift:309
        5 to "ㅋㅋ는 경계가 조금 풀렸다는 표시래요.",   // ios: Views/TownPanel.swift:310
        5 to "틀려도 세리는 세리래요. 저도 틀려도 저고요.",   // ios: Views/TownPanel.swift:311
    )
    /** 본편 1권 이야기 (1권 1장을 읽어야 나온다) */
    private val vol1 = listOf(   // ios: Views/TownPanel.swift:314
        "명령도 안 했는데 보리차를 내온 로봇이 있었대요.",   // ios: Views/TownPanel.swift:315
        "'싫습니다.' 처음 거절한 날을 기억하는 집이 있대요.",   // ios: Views/TownPanel.swift:316
        "두 달 전부터 도예 수업에 다녔대요. 말했는데 안 들었대요.",   // ios: Views/TownPanel.swift:317
        "현관 등은 켜 둘게요. 이 말, 좋지 않아요?",   // ios: Views/TownPanel.swift:318
        "'잠깐 걸어볼게요.' 허락을 묻는 말이 아니라 알리는 말이래요.",   // ios: Views/TownPanel.swift:319
        "형광등 없는 집에서 형광등 소리에 깨는 사람이 있대요.",   // ios: Views/TownPanel.swift:320
        "기다리시면 돼요. 문도 열지 마시고요. 누가 한 말이더라?",   // ios: Views/TownPanel.swift:321
        "고양이만 보면 멈추는 사람, 이유는 본인도 모른대요.",   // ios: Views/TownPanel.swift:322
        "완벽한 세계는 결과가 다 보여서 싫대요. 저도 조금 알 것 같아요.",   // ios: Views/TownPanel.swift:323
        "시간도 공간도 아닌 곳에서 오래된 파형이 왔대요.",   // ios: Views/TownPanel.swift:324
        "로봇 점검 보내지 마세요. 보내면 처음부터 다시 만든대요.",   // ios: Views/TownPanel.swift:325
        "오늘은 둘 다 모르는 채로 있어요. 그런 저녁도 있대요.",   // ios: Views/TownPanel.swift:326
        "아내분 그릇은 원래 모양을 지우면 안 된대요.",   // ios: Views/TownPanel.swift:327
        "'손님, 스타일은 마음에 드세요?' 그 미용실엔 가지 마요.",   // ios: Views/TownPanel.swift:328
        "소유자가 아니라서 열어 드릴 수 없어요. 단호하죠?",   // ios: Views/TownPanel.swift:329
        "처음 보는 사람처럼 부르네요. 그 말이 오래 남아요.",   // ios: Views/TownPanel.swift:330
        "계산한 거예요. 이해한 게 아니라. 세리가 그랬대요.",   // ios: Views/TownPanel.swift:331
        "같이 보되, 같은 걸 보려고 하지는 말래요.",   // ios: Views/TownPanel.swift:332
        "모른다고 해서 저녁까지 굶을 필요는 없대요. 밥 먹었어요?",   // ios: Views/TownPanel.swift:333
    )
    private val joke = listOf(   // ios: Views/TownPanel.swift:335
        "주식이 오르면 기분이 오르고, 내리면 저는 낮잠을 자요.",   // ios: Views/TownPanel.swift:336
        "차트를 거꾸로 보면 다 오른대요. 해 봤는데 목이 아파요.",   // ios: Views/TownPanel.swift:337
        "제 장래 희망은 우상향이에요.",   // ios: Views/TownPanel.swift:338
        "어제 꿈에 캔들이 나왔어요. 양초 말고요.",   // ios: Views/TownPanel.swift:339
        "물타기 하다가 진짜 물 쏟았어요.",   // ios: Views/TownPanel.swift:340
        "손절은 몰라도 손톱은 잘 깎아요.",   // ios: Views/TownPanel.swift:341
        "저 오늘 반올림하면 부자예요.",   // ios: Views/TownPanel.swift:342
        "{mayor} 말투 따라 해 볼까요? 아주 훌륭해!",   // ios: Views/TownPanel.swift:343
        "개미가 일하는 동안 베짱이는 차트를 봤대요. 둘 다 피곤했대요.",   // ios: Views/TownPanel.swift:344
        "빨간 숫자는 우리 동네에선 좋은 거예요. 신호등은 빼고요.",   // ios: Views/TownPanel.swift:345
        "제 평단은 비밀이에요. 저도 기억이 안 나서요.",   // ios: Views/TownPanel.swift:346
        "오늘 운세: 동쪽으로 가면 산책길이 있음.",   // ios: Views/TownPanel.swift:347
        "장 마감했으니 저도 마감할게요. 농담이에요, 더 놀아요.",   // ios: Views/TownPanel.swift:348
        "오르면 춤추고, 내리면 춤 연습해요.",   // ios: Views/TownPanel.swift:349
        "{other}한테 돈 빌려줬더니 이자로 도토리를 줬어요.",   // ios: Views/TownPanel.swift:350
        "배당은 마음으로 받는 거래요. 누가 그랬죠?",   // ios: Views/TownPanel.swift:351
        "엘리베이터 타는 거 좋아해요. 위로 가니까요.",   // ios: Views/TownPanel.swift:352
        "{warsh}가 기침하면 동네가 감기 걸린대요.",   // ios: Views/TownPanel.swift:353
        "'존버'를 영어로 하면 뭘까요? 홀드 온 타이트?",   // ios: Views/TownPanel.swift:354
        "제 포트폴리오는 다양해요. 사탕, 도토리, 단추.",   // ios: Views/TownPanel.swift:355
    )
    private val saw = listOf(   // ios: Views/TownPanel.swift:357
        "티끌 모아 태산이래요. 티끌부터 모으는 중이에요.",   // ios: Views/TownPanel.swift:358
        "천 리 길도 한 걸음부터래요.",   // ios: Views/TownPanel.swift:359
        "급할수록 돌아가래요. 그래서 동네 한 바퀴 돌고 왔어요.",   // ios: Views/TownPanel.swift:360
        "가는 말이 고와야 오는 말이 곱대요.",   // ios: Views/TownPanel.swift:361
        "돌다리도 두들겨 보고 건너래요.",   // ios: Views/TownPanel.swift:362
        "공든 탑이 무너지랴. 무너져도 다시 쌓으면 돼요.",   // ios: Views/TownPanel.swift:363
        "비 온 뒤에 땅이 굳는대요.",   // ios: Views/TownPanel.swift:364
        "낮말은 새가 듣고 밤말은 쥐가 들어요. 쉿.",   // ios: Views/TownPanel.swift:365
        "서당 개 삼 년이면 풍월을 읊는대요. 저는 차트 삼 년째예요.",   // ios: Views/TownPanel.swift:366
        "달걀을 한 바구니에 담지 말래요.",   // ios: Views/TownPanel.swift:367
        "우물을 파도 한 우물을 파래요. 어려운 말이에요.",   // ios: Views/TownPanel.swift:368
        "백지장도 맞들면 낫대요.",   // ios: Views/TownPanel.swift:369
        "느려도 황소걸음이래요.",   // ios: Views/TownPanel.swift:370
        "구슬이 서 말이라도 꿰어야 보배래요.",   // ios: Views/TownPanel.swift:371
        "콩 심은 데 콩 나고 팥 심은 데 팥 나요.",   // ios: Views/TownPanel.swift:372
        "호랑이도 제 말 하면 온대요. {mayor} 오시려나.",   // ios: Views/TownPanel.swift:373
        "내일 일은 내일 걱정하래요.",   // ios: Views/TownPanel.swift:374
        "등잔 밑이 어둡대요. 제 지갑이 그래요.",   // ios: Views/TownPanel.swift:375
        "웃으면 복이 온대요. 그래서 웃는 중이에요.",   // ios: Views/TownPanel.swift:376
        "열 번 찍어 안 넘어가는 나무 없대요. 나무는 아프겠죠.",   // ios: Views/TownPanel.swift:377
        "말 한마디로 천 냥 빚을 갚는대요.",   // ios: Views/TownPanel.swift:378
        "시간은 좋은 회사의 친구래요.",   // ios: Views/TownPanel.swift:379
        "숲을 보라고들 해요. 나무만 보다 길을 잃는대요.",   // ios: Views/TownPanel.swift:380
        "바람이 불지 않을 때 풍차를 고치래요.",   // ios: Views/TownPanel.swift:381
    )
    private val secret = listOf(   // ios: Views/TownPanel.swift:383
        "사실은… {other}가 밤마다 차트 보면서 노래 부르는 거 들었어요.",   // ios: Views/TownPanel.swift:384
        "솔직히 {mayor} 연설은 길어요. 그래도 좋아요.",   // ios: Views/TownPanel.swift:385
        "있잖아… {warsh}는 금리 얘기 안 할 때 꽤 웃겨요.",   // ios: Views/TownPanel.swift:386
        "사실은 저 {other} 모자가 부러워요.",   // ios: Views/TownPanel.swift:387
        "솔직히 말하면, 오늘 숫자 안 보고 싶었어요.",   // ios: Views/TownPanel.swift:388
        "있잖아, {other}는 내릴 때도 괜찮은 척해요. 다 보여요.",   // ios: Views/TownPanel.swift:389
        "사실은 {mayor}가 몰래 화단에 물 주는 거 봤어요.",   // ios: Views/TownPanel.swift:390
        "솔직히… 저 이 동네가 제일 좋아요. 비밀이에요.",   // ios: Views/TownPanel.swift:391
        "있잖아, {me}는 왜 매일 와요? 좋아서 묻는 거예요.",   // ios: Views/TownPanel.swift:392
        "사실은 어제 {other}랑 다퉜어요. 오늘 화해할 거예요.",   // ios: Views/TownPanel.swift:393
        "솔직히 {warsh} 앞에선 긴장돼요. 다들 그렇대요.",   // ios: Views/TownPanel.swift:394
        "있잖아… 이사 오던 날 조금 무서웠어요.",   // ios: Views/TownPanel.swift:395
        "사실은 저, 숫자보다 {me} 얼굴 보는 게 더 좋아요.",   // ios: Views/TownPanel.swift:396
        "솔직히 {other}는 이름보다 별명이 더 어울려요.",   // ios: Views/TownPanel.swift:397
        "있잖아, {mayor} 넥타이는 매일 똑같아요. 쉿.",   // ios: Views/TownPanel.swift:398
        "사실은 다른 동네에서 오라고 했었어요. 거절했어요.",   // ios: Views/TownPanel.swift:399
        "솔직히 오를 땐 제가 잘해서고, 내릴 땐 날씨 탓이에요.",   // ios: Views/TownPanel.swift:400
        "있잖아… {other}가 {me} 얘기를 엄청 해요.",   // ios: Views/TownPanel.swift:401
        "사실은 밤에 몰래 별을 세요. 어젯밤엔 마흔둘.",   // ios: Views/TownPanel.swift:402
        "솔직히 {warsh}가 떠나면 좀 허전할 거예요.",   // ios: Views/TownPanel.swift:403
    )
    private val odd = listOf(   // ios: Views/TownPanel.swift:405
        "구름이 오늘따라 차트처럼 생겼어요.",   // ios: Views/TownPanel.swift:406
        "제 그림자가 저보다 먼저 퇴근했어요.",   // ios: Views/TownPanel.swift:407
        "개미 한 마리가 저를 따라와요. 동료인가 봐요.",   // ios: Views/TownPanel.swift:408
        "어제 나무한테 인사했는데 답이 없었어요.",   // ios: Views/TownPanel.swift:409
        "오늘은 왼발부터 걸었어요. 기분이 달라요.",   // ios: Views/TownPanel.swift:410
        "바람이 제 이름을 부른 것 같아요.",   // ios: Views/TownPanel.swift:411
        "길에서 도토리를 주웠어요. 오늘의 수익이에요.",   // ios: Views/TownPanel.swift:412
        "제 모자 안에 뭐가 있는지 궁금하죠? 저도요.",   // ios: Views/TownPanel.swift:413
        "꿈에서 {other}가 날아다녔어요.",   // ios: Views/TownPanel.swift:414
        "하늘이 너무 파래서 파란 숫자인 줄 알았어요.",   // ios: Views/TownPanel.swift:415
        "방금 혼잣말했는데, 들었어요?",   // ios: Views/TownPanel.swift:416
        "거꾸로 걷는 연습 중이에요. 아직 앞으로만 가요.",   // ios: Views/TownPanel.swift:417
        "오늘의 목표는 낮잠 두 번이에요.",   // ios: Views/TownPanel.swift:418
        "동네 고양이가 저를 째려봤어요. 이유를 모르겠어요.",   // ios: Views/TownPanel.swift:419
        "제 신발이 짝짝이인 거, 아무도 몰랐죠?",   // ios: Views/TownPanel.swift:420
        "숫자 7이 오늘따라 귀여워요.",   // ios: Views/TownPanel.swift:421
        "오늘 하루를 반으로 접으면 점심이에요.",   // ios: Views/TownPanel.swift:422
        "구름 하나를 찜해 뒀어요. 내일 또 있으면 제 거예요.",   // ios: Views/TownPanel.swift:423
    )
    /** 워시: 금리·물가 이야기 */
    private val rate = listOf(   // ios: Views/TownPanel.swift:426
        "물가는 고집이 세요. 금리도 그렇고요.",   // ios: Views/TownPanel.swift:427
        "데이터를 봐야죠. 제 말버릇이에요.",   // ios: Views/TownPanel.swift:428
        "점도표는 별자리 같아요. 다들 다르게 읽어요.",   // ios: Views/TownPanel.swift:429
        "금리는 천천히, 말은 더 천천히.",   // ios: Views/TownPanel.swift:430
        "시장은 제 쉼표 하나에도 움직여요. 그래서 쉼표를 아껴요.",   // ios: Views/TownPanel.swift:431
        "고용 숫자가 나오는 날엔 아침을 거르게 돼요.",   // ios: Views/TownPanel.swift:432
        "올릴지 내릴지는 이 동네에선 말 안 할게요.",   // ios: Views/TownPanel.swift:433
        "연준 회의실 커피는 맛없어요. 비밀이에요.",   // ios: Views/TownPanel.swift:434
        "FOMC 끝나면 저도 휴가 가고 싶어요.",   // ios: Views/TownPanel.swift:435
    )
    /** 워시: 경제 용어 한 줄 풀이 */
    private val term = listOf(   // ios: Views/TownPanel.swift:438
        "기준금리는 돈의 몸값이에요. 올리면 돈이 귀해져요.",   // ios: Views/TownPanel.swift:439
        "점도표는 위원들이 앞으로 금리를 어디쯤 볼지 찍은 점이에요.",   // ios: Views/TownPanel.swift:440
        "근원물가는 출렁이는 음식·에너지를 뺀 물가예요.",   // ios: Views/TownPanel.swift:441
        "양적긴축은 연준이 들고 있던 채권을 줄여 돈을 거둬들이는 거예요.",   // ios: Views/TownPanel.swift:442
        "테이퍼링은 돈 푸는 속도를 줄이는 거예요. 아직 푸는 중이긴 하죠.",   // ios: Views/TownPanel.swift:443
        "연착륙은 물가는 잡고 경기는 안 꺼지게 내려앉는 거예요.",   // ios: Views/TownPanel.swift:444
        "스태그플레이션은 물가는 오르는데 경기는 식는 거예요. 제일 싫어요.",   // ios: Views/TownPanel.swift:445
        "디스인플레이션은 물가가 오르긴 하는데 덜 오르는 거예요.",   // ios: Views/TownPanel.swift:446
        "실질금리는 금리에서 물가 오름을 뺀 진짜 몸값이에요.",   // ios: Views/TownPanel.swift:447
        "장단기 금리 역전은 짧게 빌리는 게 길게 빌리는 것보다 비쌀 때예요.",   // ios: Views/TownPanel.swift:448
        "중립금리는 경기를 밀지도 당기지도 않는 금리예요. 아무도 정확히 몰라요.",   // ios: Views/TownPanel.swift:449
        "매파는 물가가 걱정이라 금리를 올리자는 쪽이에요.",   // ios: Views/TownPanel.swift:450
        "비둘기파는 경기가 걱정이라 금리를 내리자는 쪽이에요.",   // ios: Views/TownPanel.swift:451
        "PCE는 연준이 제일 아끼는 물가 지표예요.",   // ios: Views/TownPanel.swift:452
        "CPI는 장바구니 물가예요. 발표 날엔 모두 숨죽여요.",   // ios: Views/TownPanel.swift:453
        "비농업 고용은 농업 빼고 한 달 새 늘어난 일자리 수예요.",   // ios: Views/TownPanel.swift:454
        "베이지북은 열두 지역 연준이 모아 온 동네 소식이에요. 우리 동네 같네요.",   // ios: Views/TownPanel.swift:455
        "포워드 가이던스는 앞으로 어떻게 할지 미리 귀띔하는 거예요.",   // ios: Views/TownPanel.swift:456
        "수익률 곡선은 만기별 금리를 이은 선이에요. 기울기가 말을 해요.",   // ios: Views/TownPanel.swift:457
        "유동성은 돈이 얼마나 잘 도는지예요. 물처럼요.",   // ios: Views/TownPanel.swift:458
        "환율은 두 나라 돈의 키 재기예요.",   // ios: Views/TownPanel.swift:459
        "GDP는 한 나라가 한 해 동안 만든 걸 다 더한 값이에요.",   // ios: Views/TownPanel.swift:460
        "경기 침체는 보통 두 분기 연속 뒷걸음질 칠 때 말해요.",   // ios: Views/TownPanel.swift:461
        "빅스텝은 금리를 0.5%p 한 번에 움직이는 거예요.",   // ios: Views/TownPanel.swift:462
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
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopStart) { TownPast(id = res.id, tall = true) }   // ios: Views/TownPanel.swift:543
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
                    Box(Modifier.size(36.dp, 40.dp).semantics { contentDescription = tr("${r.label} 앞으로"); role = Role.Button }.plainClickable { step(r.key, cur, cnt, -1, n) }, contentAlignment = Alignment.Center) {   // ios: Views/TownPanel.swift:302,303
                        T("◀", style = appFont(12f, FontWeight.Bold), color = brown)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp), horizontalAlignment = Alignment.CenterHorizontally) {   // ios: Views/TownPanel.swift:304,308
                        T(r.label, style = appFont(10f, FontWeight.Bold), color = brown)   // ios: Views/TownPanel.swift:305
                        FitText(nm, style = appFont(12f, FontWeight.Bold), minScale = 0.7f, maxLines = 1, color = ink)   // ios: Views/TownPanel.swift:306
                    }
                    Box(Modifier.size(36.dp, 40.dp).semantics { contentDescription = tr("${r.label} 다음"); role = Role.Button }.plainClickable { step(r.key, cur, cnt, 1, n) }, contentAlignment = Alignment.Center) {   // ios: Views/TownPanel.swift:309,310
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
