package com.naeilo.app.data

import com.naeilo.app.pending.*
import com.naeilo.app.core.calc.*
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.*
import com.naeilo.app.core.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// 내 캐릭터(본인): 쉼터 '친구' 옆 여섯 번째 칸. 부품을 골라 만들고(저장 칸 1개), 열리면 홈·위젯에서 기본 인물 대신 보인다.
// 만들면 처음부터 홈·위젯에 둘 수 있다 (2026-10-16 결정. 예전 조건 ①②는 기록용으로만 남는다).
// 캐릭터는 미션 진행(Progress)에 같이 들어가서 내 폰에 저장하기·불러오기와 기기 동기화로 종목과 함께 옮겨진다.
// 인터미션 날짜는 기기 시계가 아니라 서버 시각(TrustedClock)으로 센다 (폰 시간을 바꿔 앞당길 수 없게).

// ios: Data/Me.swift:9,10,11,12
@Serializable
data class MeChar(
    var name: String = "",
    var parts: Map<String, Int> = emptyMap(),
    var type: String = "seri",          // 매칭 전에 고른 투자 유형 (인물 id). 평정 지수가 매칭되면 그 유형을 따른다
) {
    /** 고른 번호 (예전 자리표시 시절 번호가 지금 수보다 크면 끝 번호로) */
    // ios: Data/Me.swift:18
    fun part(g: MeGroup): Int = min(parts[g.raw] ?: g.first, g.count)
    // ios: Data/Me.swift:19,20,21,22
    fun with(g: MeGroup, v: Int): MeChar {
        val p = parts.toMutableMap()
        if (g == MeGroup.color && v > 100) p["skin"] = v - 100 else p[g.raw] = v
        return copy(parts = p)
    }
    /** 고른 상태인지 (색깔 그룹의 피부색 포함) */
    // ios: Data/Me.swift:25
    fun picked(g: MeGroup, v: Int): Boolean = if (g == MeGroup.color && v > 100) (parts["skin"] ?: 1) == v - 100 else part(g) == v
    // ios: Data/Me.swift:26
    val skin: Int get() = min(max(parts["skin"] ?: 1, 1), MeColor.skin.size)
    // ios: Data/Me.swift:27
    val shownName: String get() = if (name.isEmpty()) "나" else name.take(nameMax)

    companion object {
        // ios: Data/Me.swift:13
        const val nameMax = 6     // 이름 글자 수 (공백 하나 포함): 위젯·말풍선 이름 자리
        /** 지금 저장된 내 캐릭터 (AppModel.me 가 바뀔 때 같이). 환경 없이 그리는 곳(공유 카드 그림 등)에서 쓴다 */
        // ios: Data/Me.swift:15
        var current: MeChar? = null

        /** 이름 입력 다듬기: 공백은 하나까지(앞 공백 없음), 공백 포함 6자까지 */
        // ios: Data/Me.swift:29,30,31,32,33,34,35,37
        fun clean(v: String): String {
            val out = StringBuilder(); var space = false
            for (ch in v) {
                if (out.length >= nameMax) break
                if (ch.isWhitespace()) {
                    if (space || out.isEmpty()) continue
                    space = true; out.append(" ")
                } else out.append(ch)
            }
            return out.toString()
        }
    }
}

/** 부품 그룹. 그림은 이미지 디자인 스레드가 만드는 me_* (24×34, 판 전체 크기로 겹침, MeArt.asset). 그림이 없는 번호만 자리표시 도트 */
// ios: Data/Me.swift:42,43
enum class MeGroup { hair, color, face, eyes, mouth, top, bottom, glasses, mask, prop;
    /** Swift rawValue (case 이름 그대로) */
    val raw: String get() = name
    // ios: Data/Me.swift:44
    val id: String get() = raw
    // ios: Data/Me.swift:45,46,47,48
    val label: String get() = when (this) {
        hair -> "헤어스타일"; color -> "색깔"; face -> "얼굴형"; eyes -> "눈"; mouth -> "입"
        top -> "윗옷"; bottom -> "아래옷"; glasses -> "안경"; mask -> "마스크"; prop -> "소품"
    }
    /** 고를 수 있는 수 (= 부품 이름 수). 안경·마스크·소품은 0 = 없음 */
    // ios: Data/Me.swift:52
    val count: Int get() = if (this == color) MeColor.hair.size else names[raw]?.size ?: 0
    /** 부품 이름 (o 번, 1부터) */
    // ios: Data/Me.swift:54,55,56
    fun name(o: Int): String {
        if (this == color) return if (o > 100) MeColor.skinNames[o - 101] else MeColor.names[o - 1]
        return if (o == 0) "없음" else names[raw]?.getOrNull(o - 1) ?: "$o"
    }
    // ios: Data/Me.swift:70
    val optional: Boolean get() = this == glasses || this == mask || this == prop
    // ios: Data/Me.swift:71
    val first: Int get() = if (optional) 0 else 1
    /** 색깔 그룹은 머리 색(1부터) 뒤에 피부색(101부터)을 같이 고른다 (parts["skin"]) */
    // ios: Data/Me.swift:73
    val options: List<Int> get() = if (this == color) (1..count).toList() + (1..MeColor.skin.size).map { 100 + it } else (first..count).toList()

    companion object {
        /** 부품 이름 (번호 순, me-parts/names.json) */
        // ios: Data/Me.swift:59
        val names: Map<String, List<String>> = mapOf(
        "hair" to listOf("볼륨펌", "중단발", "긴 머리", "땋은 머리", "후드", "짧은 머리", "포니테일", "양쪽 똥머리", "삐죽 머리"),  // ios: Data/Me.swift:60
        "face" to listOf("기본", "둥근", "갸름한", "각진"),  // ios: Data/Me.swift:61
        "eyes" to listOf("점 눈", "푸른 렌즈", "금빛 눈", "새까만 눈", "늦게 깜빡이는 눈", "큰 눈", "웃는 눈", "윙크"),  // ios: Data/Me.swift:62
        "mouth" to listOf("기본", "작은 입", "일자", "미소", "활짝"),  // ios: Data/Me.swift:63
        "top" to listOf("셔츠", "블라우스", "크롭 티", "긴 재킷", "은색 기체", "앞치마", "관리국 제복", "재킷", "원피스"),  // ios: Data/Me.swift:64
        "bottom" to listOf("청바지", "슬랙스", "반바지", "맨발", "긴 치마", "치마", "트레이닝"),  // ios: Data/Me.swift:65
        "glasses" to listOf("헤드셋", "동그란 안경", "네모 안경", "반무테", "선글라스"),  // ios: Data/Me.swift:66
        "mask" to listOf("이목구비 없는 얼굴", "호흡기", "흰 마스크", "검정 마스크"),  // ios: Data/Me.swift:67
        "prop" to listOf("보리차", "휴대전화", "노트", "푸른 손빛", "고양이", "두꺼운 그릇", "가위", "보자기 꾸러미", "볍씨 주머니"),  // ios: Data/Me.swift:68
        )
        /** 쌓는 순서 (아래 → 위, 이미지 디자인 스레드 시안). body 는 손·목·다리 피부 (피부색 번호), '색깔'은 머리색·피부색을 고른다 */
        // ios: Data/Me.swift:75
        val layers = listOf("body", "face", "bottom", "top", "hair", "eyes", "mouth", "mask", "glasses", "prop")
    }
}

/** Swift Double.rounded(): 반은 0에서 먼 쪽으로 */
private fun rounded(x: Double): Double = if (x < 0) -floor(-x + 0.5) else floor(x + 0.5)

/** 머리 색 (색깔 그룹) + 위젯 바탕 (인물 색 그라데이션: 강조, 진한 쪽, 밝은 쪽). 색은 0xRRGGBB (Swift UInt32) */
// ios: Data/Me.swift:79
object MeColor {
    // 이미지 디자인 스레드 확정안 (me-parts/colors.json). 배열 길이가 곧 선택지 수
    // ios: Data/Me.swift:81
    val hair: List<Long> = listOf<Long>(0x3A2A26, 0x7A5134, 0xE0B25A, 0xA9ADB4, 0xE4E6EA, 0xB5523B, 0x4A6FB0, 0x7CC8B4, 0xD8C48A)
    // ios: Data/Me.swift:82
    val names = listOf("검정", "갈색", "금발", "애쉬그레이", "은발", "빨강", "파랑", "민트", "희끗한 금빛")
    /** 위젯 바탕: 머리색을 밝게(강조) · 어둡게(진한 쪽) */
    // ios: Data/Me.swift:84
    val accent: List<Long> by lazy { hair.map { mix(it, 0xFFFFFF, 0.35) } }
    // ios: Data/Me.swift:85
    val g1: List<Long> by lazy { hair.map { mix(it, 0x10131F, 0.6) } }
    // ios: Data/Me.swift:86,87,88,89
    fun mix(a: Long, b: Long, t: Double): Long =
        listOf(16, 8, 0).fold(0L) { r, sh ->
            val x = ((a shr sh) and 0xFF).toDouble(); val y = ((b shr sh) and 0xFF).toDouble()
            r or (rounded(x + (y - x) * t).toLong() shl sh)
        }
    // ios: Data/Me.swift:92
    val skin: List<Long> = listOf<Long>(0xF6D2B0, 0xE0B08A, 0xA67A57)
    // ios: Data/Me.swift:93
    val skinNames = listOf("밝은 피부", "중간 피부", "짙은 피부")
    // ios: Data/Me.swift:94
    fun skinIdx(c: MeChar): Int = max(0, min(skin.size - 1, c.skin - 1))
    // ios: Data/Me.swift:95
    const val g0: Long = 0x161A2C
    // ios: Data/Me.swift:96
    fun idx(c: MeChar): Int = max(0, min(hair.size - 1, c.part(MeGroup.color) - 1))
}

/** 쉼터 배경: 『중첩된 현실』 권마다 대표 쉼터 하나 (라이트·다크). 그림은 이미지 디자인 스레드가 만드는 bg_<NN> (128×80 ×5, 다크는 _dark). */
// ios: Data/Me.swift:100,101,102,103,104
data class ShelterBG(val id: String, val name: String, val intro: String, val volume: String) {
    /** 쉼터 배경 고르기 칸의 짧은 말 (글줄이기 2026-10-18, 원문 intro 유지 여부는 사용자 확인 전. 벽돌 깨기는 intro 그대로) */
    // ios: Data/Me.swift:106
    val short: String get() = Shelter.bgShort[id] ?: intro
}

/** 이미지 디자인 스레드 확정안 (shelter-bg/list.json): bg_01 프리퀄, bg_02~11 본편 1~10권, bg_12 외전 */
// ios: Data/Me.swift:109,111
val Shelter.backgrounds: List<ShelterBG> get() = shelterBackgrounds
private val shelterBackgrounds: List<ShelterBG> = listOf(
        ShelterBG(id = "bg_01", name = "시뮬레이션 속 미용실", intro = "거울 셋에 조금씩 다른 얼굴. 가위 소리가 사각, 잠시 멈춤, 사각.", volume = "프리퀄"),  // ios: Data/Me.swift:112
        ShelterBG(id = "bg_02", name = "백룸의 노란 복도", intro = "노란 벽과 떨리는 형광등, 끝없는 복도. 밤에는 출구에만 노란빛 한 줄.", volume = "1권"),  // ios: Data/Me.swift:113
        ShelterBG(id = "bg_03", name = "화성 제2돔 거주 구역", intro = "아키다리아 제2돔. 붉은 평원이 보이는 창과 녹슨 크랭크 상자.", volume = "2권"),  // ios: Data/Me.swift:114
        ShelterBG(id = "bg_04", name = "아르켄의 정자", intro = "물 위 첨탑과 구름 다리, 그리고 가운데의 원형 구조물.", volume = "3권"),  // ios: Data/Me.swift:115
        ShelterBG(id = "bg_05", name = "정거장 대합실", intro = "아시아 입구와 이름 모를 입구 사이의 손바닥만 한 틈.", volume = "4권"),  // ios: Data/Me.swift:116
        ShelterBG(id = "bg_06", name = "아르켄 운하 둑", intro = "운하 건너 백 명의 마을. 감각 연구소 창에 걸린 파형.", volume = "5권"),  // ios: Data/Me.swift:117
        ShelterBG(id = "bg_07", name = "요양 시설 301호", intro = "흙길과 지붕들이 보이는 창. 손가락 몇 개만큼만 연다.", volume = "6권"),  // ios: Data/Me.swift:118
        ShelterBG(id = "bg_08", name = "해 질 녘 원형 광장", intro = "빈 현수막과 줄표 하나. 멀리 선 사람들.", volume = "7권"),  // ios: Data/Me.swift:119
        ShelterBG(id = "bg_09", name = "들판", intro = "붉은 노을과 강, 밤에는 지나치게 많은 별. 충전할 곳은 없다.", volume = "8권"),  // ios: Data/Me.swift:120
        ShelterBG(id = "bg_10", name = "화산 아래 논", intro = "응아디레조의 계단식 논과 대추나무, 연기 오르는 화산.", volume = "9권"),  // ios: Data/Me.swift:121
        ShelterBG(id = "bg_11", name = "응아디레조 집 작업실", intro = "물레와 안 구운 그릇들, 진흙으로 메운 벽 금, 마당의 우물.", volume = "10권"),  // ios: Data/Me.swift:122
        ShelterBG(id = "bg_12", name = "도시의 시오 방", intro = "도시 불빛이 보이는 방. 휴대전화 속 세리의 프로필.", volume = "외전")  // ios: Data/Me.swift:123
)
// ios: Data/Me.swift:125
val Shelter.bgShort: Map<String, String> get() = shelterBgShort
private val shelterBgShort: Map<String, String> = mapOf(
        "bg_01" to "거울 셋, 가위 소리", "bg_02" to "끝없는 노란 복도", "bg_03" to "붉은 평원이 보이는 창", "bg_04" to "물 위 첨탑과 구름 다리",  // ios: Data/Me.swift:126
        "bg_05" to "두 입구 사이의 틈", "bg_06" to "운하 건너 백 명의 마을", "bg_07" to "흙길과 지붕이 보이는 창", "bg_08" to "빈 현수막과 줄표 하나",  // ios: Data/Me.swift:127
        "bg_09" to "붉은 노을과 강", "bg_10" to "계단식 논과 화산", "bg_11" to "물레와 안 구운 그릇", "bg_12" to "도시 불빛이 보이는 방",  // ios: Data/Me.swift:128
)
// ios: Data/Me.swift:130
fun Shelter.background(id: String?): ShelterBG? = backgrounds.firstOrNull { it.id == id }

/** 소설 속 인물 샘플 조합 (me-parts/mannequins.json). 만들기 화면에서 마네킹처럼 골라 시작한다 */
// ios: Data/Me.swift:134,135,136,137,138
data class MeSample(val name: String, val about: String, val parts: Map<String, Int>) {
    val id: String get() = name
    companion object {
        // ios: Data/Me.swift:139
        val all: List<MeSample> by lazy { listOf(
        MeSample(name = "현실의 시오", about = "원래 짧게 깎은 검은 머리. 백룸에서 맨발로 5년 반을 지냄.", parts = mapOf("hair" to 6, "face" to 1, "eyes" to 1, "mouth" to 1, "top" to 1, "bottom" to 4, "glasses" to 0, "mask" to 0, "prop" to 0, "color" to 1, "skin" to 1)),  // ios: Data/Me.swift:140
        MeSample(name = "묶은 머리 시오", about = "2권부터 어중간한 길이를 고무줄 세 번으로 묶음. 0.1초 늦게 깜빡이는 눈.", parts = mapOf("hair" to 7, "face" to 1, "eyes" to 5, "mouth" to 1, "top" to 1, "bottom" to 2, "glasses" to 0, "mask" to 0, "prop" to 2, "color" to 4, "skin" to 1)),  // ios: Data/Me.swift:141
        MeSample(name = "1권 선배", about = "절반쯤 희끗한 금빛 중단발. 질문으로 말함.", parts = mapOf("hair" to 2, "face" to 1, "eyes" to 1, "mouth" to 1, "top" to 2, "bottom" to 1, "glasses" to 0, "mask" to 0, "prop" to 3, "color" to 9, "skin" to 1)),  // ios: Data/Me.swift:142
        MeSample(name = "외전 세리", about = "휴대전화 속 AI. 몸은 없고 금발 어깨 중단발 프로필을 받음.", parts = mapOf("hair" to 2, "face" to 3, "eyes" to 2, "mouth" to 1, "top" to 9, "bottom" to 6, "glasses" to 0, "mask" to 0, "prop" to 4, "color" to 3, "skin" to 1)),  // ios: Data/Me.swift:143
        MeSample(name = "아내", about = "이름이 끝까지 나오지 않음. 흙을 빚고, 묻지 않고 밥을 냄.", parts = mapOf("hair" to 8, "face" to 1, "eyes" to 7, "mouth" to 1, "top" to 6, "bottom" to 5, "glasses" to 0, "mask" to 0, "prop" to 6, "color" to 2, "skin" to 1)),  // ios: Data/Me.swift:144
        MeSample(name = "온유", about = "요양 시설에서 방마다 창문 폭을 정함. 왼쪽부터 세 갈래로 땋은 머리.", parts = mapOf("hair" to 4, "face" to 1, "eyes" to 1, "mouth" to 2, "top" to 8, "bottom" to 1, "glasses" to 0, "mask" to 0, "prop" to 0, "color" to 1, "skin" to 2)),  // ios: Data/Me.swift:145
        MeSample(name = "루", about = "배송 유닛 XX-2214. 열여섯 살쯤의 키. 아내의 보자기 꾸러미를 안고 옴.", parts = mapOf("hair" to 6, "face" to 1, "eyes" to 2, "mouth" to 3, "top" to 5, "bottom" to 2, "glasses" to 0, "mask" to 0, "prop" to 8, "color" to 5, "skin" to 1)),  // ios: Data/Me.swift:146
        MeSample(name = "미용사", about = "시뮬레이션 속 미용실. \"선택하지 않은 얼굴은 모두 잘라낼게요.\"", parts = mapOf("hair" to 9, "face" to 1, "eyes" to 1, "mouth" to 1, "top" to 6, "bottom" to 2, "glasses" to 0, "mask" to 0, "prop" to 7, "color" to 6, "skin" to 1)),  // ios: Data/Me.swift:147
        MeSample(name = "활동가", about = "잘린 문장을 현수막에 끝내 붙임. 7권에서 머리가 짧아짐.", parts = mapOf("hair" to 6, "face" to 1, "eyes" to 1, "mouth" to 1, "top" to 3, "bottom" to 7, "glasses" to 0, "mask" to 3, "prop" to 3, "color" to 2, "skin" to 2)),  // ios: Data/Me.swift:148
        MeSample(name = "담당관", about = "\"솔직히 저도 당혹스럽습니다.\" 8권에서 머리가 희끗해짐.", parts = mapOf("hair" to 6, "face" to 4, "eyes" to 1, "mouth" to 3, "top" to 8, "bottom" to 2, "glasses" to 4, "mask" to 0, "prop" to 0, "color" to 9, "skin" to 1)),  // ios: Data/Me.swift:149
        MeSample(name = "니아", about = "화성 아키다리아 제2돔 거주 구역 C의 열세 살. 매일 지구에 녹음을 보냄.", parts = mapOf("hair" to 6, "face" to 2, "eyes" to 1, "mouth" to 1, "top" to 7, "bottom" to 2, "glasses" to 0, "mask" to 2, "prop" to 2, "color" to 2, "skin" to 3)),  // ios: Data/Me.swift:150
        MeSample(name = "관리국의 수아", about = "정거장관리국 하급 연구원. \"또 올게요. 약속은 아니에요.\"", parts = mapOf("hair" to 4, "face" to 1, "eyes" to 4, "mouth" to 1, "top" to 7, "bottom" to 2, "glasses" to 0, "mask" to 0, "prop" to 9, "color" to 1, "skin" to 1))  // ios: Data/Me.swift:151
        ) }
    }
}

// MARK: 서버 시각

/** 서버 시각: 애플 서버(안 되면 naeilo.com) 응답의 Date 머리를 받고, 그 뒤로는 기기가 켜진 뒤 흐른 시간(잠자기 포함, 시계를 바꿔도 안 바뀜)을 더한다.
 *  기기를 다시 켜면 다시 받아야 한다. 받은 적이 없거나 다시 켠 뒤 인터넷이 없으면 null (그동안 인터미션 체크인은 기다린다) */
// ios: Data/Me.swift:159,160
class TrustedClock private constructor() {
    // SharedPreferences 에는 Double 이 없어 비트 그대로 Long 으로 둔다
    // ios: Data/Me.swift:162
    private var server by mutableStateOf(java.lang.Double.longBitsToDouble(Defaults.prefs.getLong("tc.server", 0)))
    // ios: Data/Me.swift:163
    private var mono by mutableStateOf(java.lang.Double.longBitsToDouble(Defaults.prefs.getLong("tc.mono", 0)))
    // ios: Data/Me.swift:164
    private var boot by mutableStateOf(Defaults.prefs.getString("tc.boot", null) ?: "")
    // ios: Data/Me.swift:165
    private var busy = false

    companion object {
        // ios: Data/Me.swift:161
        val shared: TrustedClock by lazy { TrustedClock() }
        /** 켜진 뒤 흐른 시간 (초, 잠자기 포함) */
        // ios: Data/Me.swift:168
        val monoNow: Double get() = SystemClock.elapsedRealtimeNanos() / 1e9
        /** 이번에 켜진 기기 표시 (다시 켜면 바뀐다). 안드로이드는 켜진 횟수(BOOT_COUNT). 한 번 켜진 동안 안 바뀌니 한 번만 읽는다 */
        // ios: Data/Me.swift:170
        val bootID: String by lazy {
            val n = runCatching { Settings.Global.getInt(Defaults.ctx.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)
            if (n < 0) "" else n.toString()
        }
        // ios: Data/Me.swift:202,203,205,206,207
        private val httpDate: DateTimeFormatter by lazy {
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).withZone(ZoneId.of("GMT"))
        }
    }

    // ios: Data/Me.swift:178,179,180,181,182
    val now: Instant? get() {
        if (!(server > 0 && boot == bootID)) return null
        val m = monoNow
        if (m < mono) return null
        return Instant.ofEpochMilli(((server + (m - mono)) * 1000).toLong())
    }
    /** 기기 시계가 서버 시각과 5분 넘게 다르다 (폰 시간을 바꿨다) */
    // ios: Data/Me.swift:185
    val skewed: Boolean get() = now?.let { abs(Duration.between(it, Instant.now()).toMillis() / 1000.0) > 300 } ?: false

    /** 메인에서 부른다 (상태를 바꾸므로). 네트워크만 Dispatchers.IO */
    // ios: Data/Me.swift:187,188,189,191,196,197,198,199
    suspend fun sync() {
        if (busy) return
        busy = true
        try {
            // 애플 서버가 먼저, 안 되면 naeilo.com·중계 (웹은 백업)
            for (u in listOf(URL("https://www.apple.com/"), URL(Config.site.toString()), URL(Config.relay.toString()))) {
                val d = withContext(Dispatchers.IO) { head(u) } ?: continue
                server = d.toEpochMilli() / 1000.0; mono = monoNow; boot = bootID
                val ud = Defaults.prefs.edit()
                ud.putLong("tc.server", server.toRawBits()); ud.putLong("tc.mono", mono.toRawBits()); ud.putString("tc.boot", boot); ud.apply()
                return
            }
        } finally { busy = false }
    }

    /** HEAD 요청의 Date 머리 (캐시 안 씀, 10초). 못 받으면 null */
    // ios: Data/Me.swift:192,193,194,195
    private fun head(u: URL): Instant? = runCatching {
        val r = u.openConnection() as HttpURLConnection
        r.useCaches = false; r.connectTimeout = 10 * 1000; r.readTimeout = 10 * 1000
        r.requestMethod = "HEAD"
        try {
            r.responseCode
            val h = r.getHeaderField("Date") ?: return@runCatching null
            ZonedDateTime.parse(h, httpDate).toInstant()
        } finally { r.disconnect() }
    }.getOrNull()
}

// MARK: 열림 조건·인터미션 날짜

// ios: Data/Me.swift:212
sealed class WeekGate {
    object `open` : WeekGate()
    data class wait(val days: Int) : WeekGate()
    object offline : WeekGate()
    object skew : WeekGate()
}

// ios: Data/Me.swift:214,215
private const val dayS = 86400.0

/** 회복 루트 인터미션: 다음 주차 체크인이 열렸는지. 시작한 날부터 7일마다 한 주씩 (서버 시각 기준) */
// ios: Data/Me.swift:218,219,220,221,222,223,224
val AppModel.weekGate: WeekGate get() {
    if (devAll) return WeekGate.open
    val c = TrustedClock.shared
    val now = c.now?.toEpochMilli()?.let { it / 1000.0 }; val s = interStartAt
    if (now == null || s == null) return WeekGate.offline
    if (c.skewed) return WeekGate.skew
    val left = s + weeks.size.toDouble() * 7 * dayS - now
    return if (left <= 0) WeekGate.open else WeekGate.wait(ceil(left / dayS).toInt())
}
// ios: Data/Me.swift:226,227,228,229,230,231
val AppModel.weekGateText: String? get() = when (val g = weekGate) {
    WeekGate.open -> null
    is WeekGate.wait -> "${weeks.size + 1}주차 체크인은 ${g.days}일 뒤에 열려요."
    WeekGate.offline -> "인터넷에 연결되면 날짜를 확인하고 열려요."
    WeekGate.skew -> "폰 시간이 실제 시간과 달라요. 설정 > 일반 > 날짜 및 시간에서 '자동으로 설정'을 켜 주세요."
}

/** 목표 루트 인터미션: 다음 달 체크인이 열렸는지. 시작한 날부터 30일마다 한 달씩 (서버 시각, 회복 루트와 같은 방식) */
// ios: Data/Me.swift:236,237,238,239,240,241,242
val AppModel.monthGate: WeekGate get() {
    if (devAll) return WeekGate.open
    val c = TrustedClock.shared
    val now = c.now?.toEpochMilli()?.let { it / 1000.0 }; val s = gInterStartAt
    if (now == null || s == null) return WeekGate.offline
    if (c.skewed) return WeekGate.skew
    val left = s + gWeeks.size.toDouble() * 30 * dayS - now
    return if (left <= 0) WeekGate.open else WeekGate.wait(ceil(left / dayS).toInt())
}
// ios: Data/Me.swift:244,245,246,247,248,249
val AppModel.monthGateText: String? get() = when (val g = monthGate) {
    WeekGate.open -> null
    is WeekGate.wait -> "${gWeeks.size + 1}달째 체크인은 ${g.days}일 뒤에 열려요."
    WeekGate.offline -> "인터넷에 연결되면 날짜를 확인하고 열려요."
    WeekGate.skew -> "폰 시간이 실제 시간과 달라요. 설정 > 일반 > 날짜 및 시간에서 '자동으로 설정'을 켜 주세요."
}

/** 서버 시각을 받은 뒤: 인터미션·목표 루트 시작 날을 정하고, 내 캐릭터 1차 조건을 확인한다 */
// ios: Data/Me.swift:254,255,256,258,259,260,261
fun AppModel.fixStarts() {
    val c = TrustedClock.shared
    val now = c.now?.toEpochMilli()?.let { it / 1000.0 } ?: return
    if (c.skewed) return
    // 예전 진행(시작 날 없음)은 이미 지난 주만큼 앞당겨 둔다
    if (route == Route.recover && done.contains(5) && interStartAt == null) interStartAt = now - weeks.size.toDouble() * 7 * dayS
    if (isGoal && goalStartAt == null) goalStartAt = now
    if (isGoal && gDone.contains("link") && gInterStartAt == null) gInterStartAt = now - gWeeks.size.toDouble() * 30 * dayS
    if (meGate1At == null && meGate1Live(now)) meGate1At = now
}
// ios: Data/Me.swift:263,264
private fun AppModel.meGate1Live(now: Double): Boolean =
    if (route == Route.recover) done.contains(6) else goalStartAt?.let { now - it >= 28 * dayS } ?: false

/** ① 회복 루트 인터미션 모두 / 목표 루트 4주 */
// ios: Data/Me.swift:268
val AppModel.meGate1: Boolean get() = meGate1At != null || (route == Route.recover && done.contains(6))
// ios: Data/Me.swift:269,270,271,272,273
val AppModel.meGate1Text: String get() {
    if (meGate1) return if (route == Route.recover) "회복 루트 인터미션 4주 모두" else "목표 루트 4주"
    if (route == Route.recover) return "회복 루트 인터미션 4주 모두 (${weeks.size}/4주)"
    val s = goalStartAt; val now = TrustedClock.shared.now?.toEpochMilli()?.let { it / 1000.0 }
    if (s == null || now == null) return "목표 루트에서 4주 지나기"
    return "목표 루트에서 4주 지나기 (${max(1, ceil((s + 28 * dayS - now) / dayS).toInt())}일 남음)"
}
/** ① 소식함 체크리스트용 짧은 문구 (루트별) */
// ios: Data/Me.swift:276,277,278,279,280
val AppModel.meGate1Short: String get() {
    if (route == Route.recover) return if (meGate1) "4주 체크인 마치기" else "4주 체크인 마치기 · ${weeks.size}/4주"
    if (meGate1) return "목표를 정한 뒤 4주 지나기"
    val s = goalStartAt; val now = TrustedClock.shared.now?.toEpochMilli()?.let { it / 1000.0 }
    if (s == null || now == null) return "목표를 정한 뒤 4주 지나기"
    return "목표를 정한 뒤 4주 지나기 · ${max(1, ceil((s + 28 * dayS - now) / dayS).toInt())}일 남음"
}
/** ② 평정 지수 성향 매칭 */
// ios: Data/Me.swift:283
val AppModel.meGate2: Boolean get() = calm.type != null
/** 홈·위젯에 내 캐릭터를 둘 수 있는지: 만들면 처음부터 (2026-10-16 결정. 예전 조건 ①②는 기록만 남는다) */
// ios: Data/Me.swift:285
val AppModel.meUnlocked: Boolean get() = me != null

/** 내 캐릭터의 말투 = 투자 유형 인물 (매칭되면 평정 지수 유형, 아니면 고른 유형) */
// ios: Data/Me.swift:288
val AppModel.meVoice: String get() = calm.type ?: me?.type ?: "seri"
/** 대사·말투를 고를 인물 id ("me" 는 유형 인물) */
// ios: Data/Me.swift:290
fun AppModel.voice(id: String): String = if (id == "me") meVoice else id
// ios: Data/Me.swift:291,292
fun AppModel.friendName(id: String): String =
    if (id == "me") (me?.shownName ?: "나") else Shelter.friends.firstOrNull { it.id == id }?.name ?: "세리"
/** 열린 쉼터 배경 수: 처음 연 날부터 하루 2곳 (2026-10-16 결정). 기본 방은 늘 열림 */
// ios: Data/Me.swift:295
val AppModel.bgOpen: Int get() = if (devAll) Shelter.backgrounds.size else min(Shelter.backgrounds.size, 2 * Support.shared.dayN)
// ios: Data/Me.swift:296
fun AppModel.bgOn(id: String?): Boolean =
    id?.let { i -> Shelter.backgrounds.indexOfFirst { it.id == i }.takeIf { it >= 0 } }?.let { it < bgOpen } ?: true
/** 이 배경이 열리는 날 (처음 연 날 = 1일째) */
// ios: Data/Me.swift:298
fun AppModel.bgDay(id: String): Int = (Shelter.backgrounds.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0) / 2 + 1
/** 지금 쉼터 방 그림 (배경을 고르지 않았거나 잠기면 기본 방) */
// ios: Data/Me.swift:300
val AppModel.roomBG: String? get() = if (bgOn(meBg)) meBg else null
