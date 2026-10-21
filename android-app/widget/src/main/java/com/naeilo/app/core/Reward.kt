package com.naeilo.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

// String(localized:) 는 WL.t 로 옮긴다 (안드로이드는 String Catalog 대신 lines_en.json 표 하나)

/** 인물 보상 위젯이 읽는 숫자 (앱이 reward.json 으로 쓴다). 모두 앱의 계산값이고, 위젯은 그리기만 한다 */
// ios: WidgetShared/Reward.swift:5
@Serializable
data class WReward(
    // ios: WidgetShared/Reward.swift:7
    var keyName: String,          // "본전" 또는 "목표"
    // ios: WidgetShared/Reward.swift:8
    var pct: Double,              // 본전(목표)까지 진행 0~1
    // ios: WidgetShared/Reward.swift:9
    var pctYesterday: Double,
    // ios: WidgetShared/Reward.swift:10
    var remain: Double,           // 남은 금액 (원)
    // ios: WidgetShared/Reward.swift:11
    var cells: Int,               // 1000칸 중 채운 칸
    // ios: WidgetShared/Reward.swift:12
    var cellsYesterday: Int,
    // ios: WidgetShared/Reward.swift:13
    var total: Double,            // 평가액 (원)
    // ios: WidgetShared/Reward.swift:14
    var dayChg: Double,           // 오늘 등락 (원화 평가액 기준)
    // ios: WidgetShared/Reward.swift:15
    var tiles: List<Tile>,
    // ios: WidgetShared/Reward.swift:16
    var next: String? = null,     // "DRNK 실적 D-12 (10/22)"
    // ios: WidgetShared/Reward.swift:17
    var friendsOn: List<String>,  // 만난 인물 (세리는 처음부터)
    // ios: WidgetShared/Reward.swift:18
    var homeFriend: String? = null, // 앱 홈에 둔 인물 (위젯 '홈 인물 따라가기'가 쓴다)
    // ios: WidgetShared/Reward.swift:19
    var spark: List<Double>? = null,    // 최근 10일 평가액 (만원). 인물 · 자산 추이
    // ios: WidgetShared/Reward.swift:20
    var items: List<String>? = null,    // 쉼터에 돌아온 물건 (돌아온 순서). 오늘의 움직임 위젯이 쓴다
    // ios: WidgetShared/Reward.swift:21
    var says: Map<String, String>? = null,  // 인물별 말풍선 (앱이 대사 데이터에서 골라 넘긴다)
    // ios: WidgetShared/Reward.swift:22
    var saysNext: Map<String, List<String>>? = null,  // 인물별 다음 대사 후보. 위젯이 갱신하거나 ↻를 누를 때마다 다음 줄
    // ios: WidgetShared/Reward.swift:23
    var news: String? = null,       // 홈 리마인드: 오늘 생긴 큰 달성 (오늘의 움직임 말풍선)
    // ios: WidgetShared/Reward.swift:24
    var badge: String? = null,      // 홈 리마인드: 오늘 넘은 1000칸 이정표 (블록 위젯 머리)
    // ios: WidgetShared/Reward.swift:25
    var newsDay: String? = null,    // news·badge 를 쓴 날 (다른 날이면 안 보인다)
) {
    // ios: WidgetShared/Reward.swift:6
    @Serializable data class Tile(val t: String, val w: Double, val c: Double)   // 종목 이름, 비중, 오늘 등락

    // ios: WidgetShared/Reward.swift:26
    val newsToday: String? get() = if (newsDay == Day.today) news else null
    // ios: WidgetShared/Reward.swift:27
    val badgeToday: String? get() = if (newsDay == Day.today) badge else null

    companion object {
        // ios: WidgetShared/Reward.swift:29,30,31,32,33
        val sample: WReward by lazy {
            WReward(keyName = WL.t("본전"), pct = 0.87, pctYesterday = 0.862, remain = 1.52e7, cells = 264, cellsYesterday = 261,
                total = 1.234e8, dayChg = 0.008, tiles = listOf(Tile(t = "DRNK", w = 0.62, c = 0.019), Tile(t = "QQQ", w = 0.38, c = -0.004)),
                next = WL.t("DRNK 실적 D-12 (10/22)"), friendsOn = WChar.all.map { it.id },
                spark = listOf(11980.0, 12050.0, 12010.0, 12120.0, 12200.0, 12150.0, 12260.0, 12310.0, 12240.0, 12340.0),
                items = listOf("barley_tea", "porch_light"))
        }
    }
}

/** 인물 위젯의 인물: 색은 캐릭터 설정 스레드 시안 (reward-widgets) */
// ios: WidgetShared/Reward.swift:37,38,39,40
data class WChar(
    val id: String, val name: String, val week: Int,
    val accent: Long, val g0: Long, val g1: Long,
    var voice: String? = null,    // 말투 인물 (내 캐릭터는 투자 유형 인물)
) {
    /** 위젯에 쓰는 이름 (영어 기기면 Seri 처럼, 내 캐릭터는 지은 이름 그대로) */
    // ios: WidgetShared/Reward.swift:49
    val title: String get() = WL.t(name)

    /** 말풍선 (인물 말투). 세리·시오·선배는 오늘 가장 크게 움직인 종목을 넣는다.
     *  사흘에 한 번은 쉼터에 돌아온 물건 얘기를 인물 말투로 한다 */
    // ios: WidgetShared/Reward.swift:57,58,59,60,61
    fun line(r: WReward): String {
        r.saysNext?.get(id)?.let { l -> if (l.isNotEmpty()) return l[WidgetLine.index() % l.size] }
        r.says?.get(id)?.let { s -> if (s.isNotEmpty()) return s }
        val v = voice ?: id
        if (!WL.en) { val it = WItem.today(r); val say = it?.let { i -> WItem.say(i, v) }; if (say != null) return say }
        // ios: WidgetShared/Reward.swift:62,63
        val top = r.tiles.maxByOrNull { abs(it.c) }
        val t = top?.t ?: WL.t("오늘"); val c = top?.c ?: 0.0
        // ios: WidgetShared/Reward.swift:64,65,66,67,68,69,70,71
        if (WL.en) {
            val n = cformat("%.1f", abs(c) * 100)
            return when (v) {
                "seri" -> "…… $t moved first."
                "sio" -> "$t $n. Counted."
                "seonbae" -> if (r.dayChg >= 0) "Up today. Why do you think?" else "Down today. Why do you think?"
                "ir" -> "Shaky, but don't hurry."
                else -> "Right on time today."
            }
        }
        // ios: WidgetShared/Reward.swift:74,75,77,78,79
        return when (v) {
            "seri" -> "……$t${josa(t)} 먼저 움직였어요."
            "sio" -> "$t ${cformat("%.1f", abs(c) * 100)}. 셌어."
            "seonbae" -> if (r.dayChg >= 0) WL.t("올랐네. 왜 올랐다고 생각해?") else WL.t("내렸네. 왜 내렸다고 생각해?")
            "ir" -> WL.t("흔들려도 서두르지 마.")
            else -> WL.t("오늘은 제때 왔어요.")
        }
    }

    companion object {
        // ios: WidgetShared/Reward.swift:41,42,43,44,45,46
        val all: List<WChar> by lazy {
            listOf(
                WChar(id = "seri", name = WL.t("세리"), week = 0, accent = 0x5ab8ffL, g0 = 0x16203fL, g1 = 0x2c3e8aL),
                WChar(id = "sio", name = WL.t("시오"), week = 1, accent = 0x7fd6ffL, g0 = 0x122838L, g1 = 0x265c76L),
                WChar(id = "seonbae", name = WL.t("선배"), week = 2, accent = 0xe0b25aL, g0 = 0x301e2cL, g1 = 0x744658L),
                WChar(id = "ir", name = WL.t("이르"), week = 3, accent = 0xffd36bL, g0 = 0x1a1830L, g1 = 0x4a3f72L),
                WChar(id = "sua", name = WL.t("수아"), week = 4, accent = 0xf2a070L, g0 = 0x3a2228L, g1 = 0xb06448L),
            )
        }
        // ios: WidgetShared/Reward.swift:50,51,52
        fun of(id: String): WChar {
            if (id == "me") { val m = WMe.saved; if (m != null) return WChar(id = "me", name = m.name, week = 0, accent = m.accent, g0 = m.g0, g1 = m.g1, voice = m.voice) }
            return all.firstOrNull { it.id == id } ?: all[0]
        }

        /** 이/가: 한글은 받침, 영문 티커는 읽는 소리 (L·M·N·R 로 끝나면 받침) */
        // ios: WidgetShared/Reward.swift:83,84,85,86,87
        fun josa(s: String): String {
            if (WL.en) return ""   // 영어엔 조사가 없다
            if (s.isEmpty()) return WL.t("가")
            val ch = s.codePointBefore(s.length)   // 마지막 글자 (unicodeScalars.last)
            if (ch in 0xAC00..0xD7A3) return if ((ch - 0xAC00) % 28 == 0) WL.t("가") else WL.t("이")
            return if ("LMNR".codePoints().anyMatch { it == ch }) WL.t("이") else WL.t("가")
        }
    }
}

/** 내 캐릭터 (앱이 열림 조건을 채웠을 때만 me.json · me.png 로 쓴다, MeViews MeArt.push) */
// ios: WidgetShared/Reward.swift:92,93,94,95
@Serializable
data class WMe(
    var name: String,
    var voice: String,
    var accent: Long, var g0: Long, var g1: Long,
) {
    companion object {
        // ios: WidgetShared/Reward.swift:96
        val saved: WMe? get() = Store.read(serializer(), "me.json")
        // ios: WidgetShared/Reward.swift:97
        val image: Bitmap? get() = Store.readData("me.png")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        /** w_me → me.png, w_me_pose_<동작> → me_pose_<동작>.png (없으면 me.png). 앱이 MePose 로 그려 둔다 */
        // ios: WidgetShared/Reward.swift:99,100
        fun image(name: String): Bitmap? =
            Store.readData(name.drop(2) + ".png")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: image
    }
}

/** 쉼터에 돌아온 물건: 위젯 그림 이름은 w_item_<id> (앱 쪽 art_<id> 와 같은 도트) */
// ios: WidgetShared/Reward.swift:105
object WItem {
    /** 물건마다 인물 말투 (세리 관찰·해요체, 시오 짧은 반말, 선배 질문, 이르 조용한 반말, 수아 해요체) */
    // ios: WidgetShared/Reward.swift:107
    val lines: Map<String, Map<String, String>> by lazy { mapOf(
        // ios: WidgetShared/Reward.swift:108
        "barley_tea" to mapOf("seri" to WL.t("보리차 내 뒀어요. 시키지 않았어요."), "sio" to WL.t("보리차. 아직 따뜻해."), "seonbae" to WL.t("보리차, 누가 내 놨을까?"), "ir" to WL.t("따뜻할 때 마셔."), "sua" to WL.t("보리차 식기 전에 왔어요.")),
        // ios: WidgetShared/Reward.swift:109
        "porch_light" to mapOf("seri" to WL.t("현관 등, 켜져 있어요."), "sio" to WL.t("등은 켜 뒀어. 늦어도 돼."), "seonbae" to WL.t("늦게 와도 켜져 있는 불, 왜일까?"), "ir" to WL.t("불은 꺼지지 않아."), "sua" to WL.t("현관 등 보고 찾아왔어요.")),
        // ios: WidgetShared/Reward.swift:110
        "wall_clock" to mapOf("seri" to WL.t("시계가 3분 늦어요. 그대로 둘게요."), "sio" to WL.t("시계는 안 맞췄어. 셀 거라서."), "seonbae" to WL.t("시간을 꼭 맞춰야 할까?"), "ir" to WL.t("빠르다고 먼저 닿는 건 아니야."), "sua" to WL.t("시계는 늦어도 저는 제때예요.")),
        // ios: WidgetShared/Reward.swift:111
        "hair_tie" to mapOf("seri" to WL.t("머리끈, 식탁 위에 있어요."), "sio" to WL.t("머리끈. 아직 어중간해."), "seonbae" to WL.t("자르지 않고 묶은 이유가 뭐야?"), "ir" to WL.t("묶어 두면 덜 흔들려."), "sua" to WL.t("머리끈 하나 빌려 갈게요.")),
        // ios: WidgetShared/Reward.swift:112
        "table_chair" to mapOf("seri" to WL.t("종이 한 장, 비어 있어요."), "sio" to WL.t("식탁에 종이. 내일 쓸 거야."), "seonbae" to WL.t("그 종이에 뭐라고 쓸 거야?"), "ir" to WL.t("자리 하나 비워 뒀어."), "sua" to WL.t("제 자리 아직 있어요?")),
        // ios: WidgetShared/Reward.swift:113
        "asym_bowl" to mapOf("seri" to WL.t("그릇이 2도 기울었어요. 고치지 않을게요."), "sio" to WL.t("기운 그릇. 그대로 둬."), "seonbae" to WL.t("기울면 틀린 걸까?"), "ir" to WL.t("기운 채로도 담겨."), "sua" to WL.t("기운 그릇이 제 거예요.")),
        // ios: WidgetShared/Reward.swift:114
        "unfired_bowl" to mapOf("seri" to WL.t("그릇은 아직 안 구웠어요."), "sio" to WL.t("아직 안 구웠어. 급할 거 없어."), "seonbae" to WL.t("언제 구울지는 누가 정해?"), "ir" to WL.t("서두르면 금이 가."), "sua" to WL.t("다 마르면 구울게요. 약속은 아니에요.")),
        // ios: WidgetShared/Reward.swift:115
        "jujube_seed" to mapOf("seri" to WL.t("대추씨, 싹은 아직이에요."), "sio" to WL.t("대추씨 심은 지 며칠. 세고 있어."), "seonbae" to WL.t("기다리는 동안 뭘 했어?"), "ir" to WL.t("기다림도 자라."), "sua" to WL.t("대추씨 아직 기다리는 중이에요.")),
        // ios: WidgetShared/Reward.swift:116
        "elder_bead" to mapOf("seri" to WL.t("구슬은 하나뿐이에요."), "sio" to WL.t("구슬. 하나야."), "seonbae" to WL.t("하나뿐인 걸 왜 여기 뒀을까?"), "ir" to WL.t("원로가 준 거야. 하나뿐이야."), "sua" to WL.t("구슬에 제 얼굴이 비쳐요.")),
        // ios: WidgetShared/Reward.swift:117
        "rice_seeds" to mapOf("seri" to WL.t("볍씨 주머니, 챙겨 뒀어요."), "sio" to WL.t("볍씨. 누가 싸 줬는지 알아."), "seonbae" to WL.t("누가 싸 줬는지 기억나?"), "ir" to WL.t("심을 곳은 네가 정해."), "sua" to WL.t("볍씨 주머니 들고 왔어요.")),
    ) }
    // ios: WidgetShared/Reward.swift:119
    fun say(item: String, who: String): String? = lines[item]?.get(who) ?: lines[item]?.get("seri")
    /** 오늘 말풍선에 나오는 물건: 사흘에 한 번, 돌아온 물건을 차례로. 나머지 날은 null */
    // ios: WidgetShared/Reward.swift:121,122,123,124
    fun today(r: WReward, date: LocalDate = LocalDate.now(ZoneId.systemDefault())): String? {
        val ids = r.items?.takeIf { it.isNotEmpty() } ?: return null
        val n = ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), date) + 1   // 서기 1년 1월 1일부터 몇째 날 (ordinality .day in .era)
        return if (n % 3 == 0L) ids[((n / 3) % ids.size).toInt()] else null
    }
    /** 인물 발치에 둘 물건: 말풍선에 나온 물건, 아니면 마지막에 돌아온 물건 */
    // ios: WidgetShared/Reward.swift:127
    fun shown(r: WReward): String? = today(r) ?: r.items?.lastOrNull()
}

/** 위젯 대사 차례: 앱이 마지막으로 쓴 뒤 위젯이 새로 그려진 횟수 (↻ 포함). 앱이 다시 쓰면 0 */
// ios: WidgetShared/Reward.swift:131
object WidgetLine {
    // ios: WidgetShared/Reward.swift:132
    fun index(): Int = Store.defaults.getInt("lineTaps", 0)
    /** 위젯이 새로 그려질 때. 여러 위젯이 한꺼번에 그려져도 한 번만 센다 */
    // ios: WidgetShared/Reward.swift:134,135,136,137,138
    fun bump(minGap: Double = 300.0) {
        val d = Store.defaults; val now = System.currentTimeMillis() / 1000.0
        if (now - d.getDouble("lineBumpAt") <= minGap) return
        d.edit().putDouble("lineBumpAt", now).putInt("lineTaps", index() + 1).apply()
    }
    // ios: WidgetShared/Reward.swift:140
    fun reset() { Store.defaults.edit().putInt("lineTaps", 0).putDouble("lineBumpAt", System.currentTimeMillis() / 1000.0).apply() }
}

/** 작은 위젯 내 길·목표 페이스가 읽는 값 (앱이 small.json 으로 쓴다). 모두 앱의 계산값이다 */
// ios: WidgetShared/Reward.swift:144
@Serializable
data class WSmall(
    // ios: WidgetShared/Reward.swift:145
    var path: List<List<Double>>,  // 내 길 실제 평가액 [f, 원]: f = 시작 0 → 목표일 1. 마지막 점 = 앱이 쓴 때의 지금
    // ios: WidgetShared/Reward.swift:146
    var need: List<Double>,        // 계획선 41점 (f = 0, 0.025 … 1)
    // ios: WidgetShared/Reward.swift:147
    var gap: Double,               // 계획선보다 앞섬(+)·뒤처짐(−) (앱 내 길 화면과 같은 값)
    // ios: WidgetShared/Reward.swift:148
    var t0: Double,                // 앱이 쓴 때의 위젯 총액. 위젯은 지금 총액과의 비율로 지금 점·앞섬을 맞춘다
    // ios: WidgetShared/Reward.swift:149
    var me: Double? = null,        // 평행 세계: 같은 날 출발한 뒤 진짜 나의 수익률 (모의 바구니가 없으면 null)
    // ios: WidgetShared/Reward.swift:150
    var basket: Double? = null,    // 모의 바구니 수익률
) {
    /** 지금 점 (앱이 쓴 뒤 받은 시세만큼) */
    // ios: WidgetShared/Reward.swift:155
    fun nowValue(total: Double): Double = (path.lastOrNull()?.lastOrNull() ?: total) * (if (t0 > 0 && total > 0) total / t0 else 1.0)
    // ios: WidgetShared/Reward.swift:156
    fun gapNow(total: Double): Double = if (t0 > 0 && total > 0) (1 + gap) * total / t0 - 1 else gap

    companion object {
        // ios: WidgetShared/Reward.swift:152
        const val file = "small.json"
        // ios: WidgetShared/Reward.swift:153
        fun load(): WSmall? = Store.read(serializer(), file)

        /** 위젯 갤러리·연결 전 예시 */
        // ios: WidgetShared/Reward.swift:159,160,161,162
        val sample: WSmall get() {
            val need = (0..40).map { 4.8e8 * (1e9 / 4.8e8).pow(it / 40.0) }
            val path = (0..10).map { i -> val f = 0.25 * i / 10; listOf(f, 4.8e8 * (1e9 / 4.8e8).pow(f) * (1 + 0.012 * sin(i.toDouble()) + 0.001 * i)) }
            return WSmall(path = path, need = need, gap = 0.011, t0 = 0.0, me = 0.084, basket = 0.078)
        }
    }
}
