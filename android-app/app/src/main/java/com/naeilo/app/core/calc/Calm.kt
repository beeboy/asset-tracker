package com.naeilo.app.core.calc

import com.naeilo.app.core.cformat
import com.naeilo.app.data.Lines
import com.naeilo.app.net.*
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.calc.CalmEvent.Act
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.persists
import com.naeilo.app.data.recordDay
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.truncate

// 평정 지수 (가위바위보 지수): 직전 결과(수익·손실)에 끌려 매매하는지를 센다.
// 점수는 '반대로 하기'가 아니라 '결과와 상관없이 정해 둔 규칙대로 하기'에 준다. 물타기는 보상하지 않는다.
// 근거: project-files research/가위바위보-심리학과-투자.md 6절, notes/가위바위보-지수-캐릭터-매칭안-2026-10-10.md

/** Swift Double.rounded() (반올림, .5 는 0에서 먼 쪽). 정수부를 빼는 계산이라 0.49999999999999994 도 0 */
private fun Double.rnd(): Double { val t = truncate(this); return if (abs(this - t) >= 0.5) t + sign(this) else t }

// ios: Data/Calm.swift:7
@Serializable
data class CalmEvent(
    var day: String,       // ios: Data/Calm.swift:9 yyyy-MM-dd
    var sym: String,  // ios: Data/Calm.swift:10
    var buy: Boolean,      // ios: Data/Calm.swift:11 산 것인지 (판 것·기다림은 false)
    var win: Boolean,      // ios: Data/Calm.swift:12 그때 그 종목이 평균 단가 위(수익)였는지
    var act: Act,  // ios: Data/Calm.swift:13
    var big: Boolean,      // ios: Data/Calm.swift:14 그 종목이 하루 ±5% 넘게 움직인 날인지
    var pts: Int,  // ios: Data/Calm.swift:15
) {
    @Serializable enum class Act { rule, chase, switchOut, avgDown, trim, wait, fresh }   // ios: Data/Calm.swift:8 fresh: 새 종목 매수
    val isTrade: Boolean get() = act != Act.wait  // ios: Data/Calm.swift:16

    // ios: Data/Calm.swift:18,19
    val label: String get() = when (act) {
        Act.rule -> "규칙대로"  // ios: Data/Calm.swift:20
        Act.chase -> "수익 뒤 추가 매수"  // ios: Data/Calm.swift:21
        Act.switchOut -> "손실 뒤 매도"  // ios: Data/Calm.swift:22
        Act.avgDown -> "손실 뒤 추가 매수"  // ios: Data/Calm.swift:23
        Act.trim -> "수익 뒤 덜어내기"  // ios: Data/Calm.swift:24
        Act.wait -> "큰 변동일 기다림"  // ios: Data/Calm.swift:25
        Act.fresh -> "새 종목 매수"  // ios: Data/Calm.swift:26
    }
}

// ios: Data/Calm.swift:31
class CalmStats {
    var n = 0              // ios: Data/Calm.swift:32 기록 수 (매매 + 기다림)
    var trades = 0  // ios: Data/Calm.swift:33
    var index = 50         // ios: Data/Calm.swift:34 0~100, 결과와 무관할수록 높다
    var chase = 0.0        // ios: Data/Calm.swift:35 수익 직후 추가 매수 비율
    var switchR = 0.0      // ios: Data/Calm.swift:36 손실 직후 매도 비율
    var waitR = 0.0        // ios: Data/Calm.swift:37 큰 변동일에 기다린 비율
    var ruleR = 0.0        // ios: Data/Calm.swift:38 매매 중 규칙대로 한 비율
    var contra = 0.0       // ios: Data/Calm.swift:39 수익 뒤 덜어내기 + 규칙 안의 역방향 매매 비율
    var perMonth = 0.0     // ios: Data/Calm.swift:40 최근 3달 한 달 평균 매매 수
    var trend: Int? = null  // ios: Data/Calm.swift:41 최근 30일 지수 − 그 전 30일 지수 (양쪽 5건 이상일 때)
    var weekPts = 0  // ios: Data/Calm.swift:42
}

/** 저장형. 바꾸는 함수(record·touch·refreshType·climb·note·takeNotes)는 이 값을 고친다.
 *  AppModel.calm 에 남기려면 Swift 처럼 copy() 를 고친 뒤 다시 넣는다 (목록·사다리는 늘 새 값으로 바꿔 넣어서 copy() 가 원래 값을 건드리지 않는다) */
// ios: Data/Calm.swift:45
@Serializable
data class Calm(
    var events: List<CalmEvent> = emptyList(),  // ios: Data/Calm.swift:46
    var type: String? = null,        // ios: Data/Calm.swift:47 지금 매칭된 인물 id
    var typeMonth: String? = null,   // ios: Data/Calm.swift:48 유형을 마지막으로 정한 달 (yyyy-MM). 한 달에 한 번만 바뀐다
    var prevType: String? = null,  // ios: Data/Calm.swift:49
    var together: List<String> = emptyList(),    // ios: Data/Calm.swift:50 지금 인물과 함께한 날 (앱을 연 날)
    var ladder: Ladder? = null,      // ios: Data/Calm.swift:51 습관 사다리: 지금 유형의 걸음 (예전 저장값엔 없어서 옵셔널)
    var notes: List<CalmNote>? = null,   // ios: Data/Calm.swift:52 홈 리마인드가 가져갈 사다리 소식 (takeNotes)
) {
    // ios: Data/Calm.swift:317
    class Step(val name: String, val what: String)
    // ios: Data/Calm.swift:171
    class Kind(val id: String, val title: String, val `when`: String, val like: String)

    companion object {
        const val weekCap = 5         // ios: Data/Calm.swift:54 한 주에 쌓을 수 있는 플러스 점수
        const val minEvents = 10      // ios: Data/Calm.swift:55 이보다 적으면 매칭하지 않는다
        const val keep = 400          // ios: Data/Calm.swift:56 남겨 두는 기록 수 (오래된 것부터 지운다)

        private const val key = "calm.v1"  // ios: Data/Calm.swift:58
        // ios: Data/Calm.swift:59,60
        fun load(): Calm =
            Defaults.prefs.getString(key, null)?.let { runCatching { NJson.decodeFromString<Calm>(it) }.getOrNull() } ?: Calm()

        // ios: Data/Calm.swift:64,65
        fun points(a: Act): Int = when (a) {
            Act.rule -> 2  // ios: Data/Calm.swift:66
            Act.wait -> 1  // ios: Data/Calm.swift:67
            Act.chase, Act.switchOut -> -1  // ios: Data/Calm.swift:68
            Act.avgDown, Act.trim, Act.fresh -> 0  // ios: Data/Calm.swift:69
        }

        // ios: Data/Calm.swift:73
        private fun week(day: String): String {
            val d = Day.date(day) ?: return day  // ios: Data/Calm.swift:74
            val c = d.atZone(ZoneId.systemDefault())  // ios: Data/Calm.swift:75 Calendar(identifier: .iso8601) 는 기기 시간대
            return "${c.get(IsoFields.WEEK_BASED_YEAR)}-${c.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"  // ios: Data/Calm.swift:76
        }

        // ios: Data/Calm.swift:120
        fun index(ev: List<CalmEvent>): Double {
            val t = ev.filter { it.isTrade }  // ios: Data/Calm.swift:121
            val w = t.filter { it.win }; val l = t.filter { !it.win }  // ios: Data/Calm.swift:122
            // 한쪽이 비어도 튀지 않게 (1, 2)를 더한 비율로 본다
            val pw = (w.count { it.buy } + 1).toDouble() / (w.size + 2).toDouble()  // ios: Data/Calm.swift:124
            val pl = (l.count { it.buy } + 1).toDouble() / (l.size + 2).toDouble()  // ios: Data/Calm.swift:125
            val raw = 100 * (1 - abs(pw - pl))  // ios: Data/Calm.swift:126
            // 매매가 10건보다 적으면 50 쪽으로 당긴다 (적은 기록으로 과장하지 않게)
            val k = min(1.0, t.size.toDouble() / 10)  // ios: Data/Calm.swift:128
            return 50 + (raw - 50) * k  // ios: Data/Calm.swift:129
        }

        /** 다섯 인물 매칭 (위에서부터 먼저 맞는 것) */
        // ios: Data/Calm.swift:159
        fun match(s: CalmStats): String? {
            if (s.n < minEvents) return null  // ios: Data/Calm.swift:160
            if (s.chase >= 0.6 || s.switchR >= 0.6) return "ir"  // ios: Data/Calm.swift:161
            val t = s.trend; if (t != null && t >= 10) return "sua"  // ios: Data/Calm.swift:162
            if (s.contra >= 0.5 && s.index >= 60) return "seonbae"  // ios: Data/Calm.swift:163
            if (s.perMonth <= 2 && s.waitR >= 0.6) return "sio"  // ios: Data/Calm.swift:164
            if (s.index >= 80 && s.ruleR >= 0.5) return "seri"  // ios: Data/Calm.swift:165
            return if (s.index >= 70) "seri" else "sua"  // ios: Data/Calm.swift:166
        }

        // MARK: 인물 문구
        val kinds: List<Kind> = listOf(  // ios: Data/Calm.swift:172
            Kind(id = "ir", title = "앞서 달리는 형", `when` = "수익 뒤 추가 매수나 손실 뒤 매도가 60% 이상",  // ios: Data/Calm.swift:173
                 like = "너무 빨랐던 사람. 연결을 다섯, 스물, 백으로 넓히다 넘쳤다."),  // ios: Data/Calm.swift:174
            Kind(id = "sua", title = "자라는 형", `when` = "최근 30일 지수가 그 전보다 10 이상 올랐을 때",  // ios: Data/Calm.swift:175
                 like = "침묵을 고르는 법을 배운다. 제때 오는 것을 몸으로 느낀다."),  // ios: Data/Calm.swift:176
            Kind(id = "seonbae", title = "되묻는 형", `when` = "수익 뒤 덜어내기·규칙 안의 역방향 매매가 절반 이상, 지수 60 이상",  // ios: Data/Calm.swift:177
                 like = "질문으로 말한다. \"그게 정말 네 생각이야.\""),  // ios: Data/Calm.swift:178
            Kind(id = "sio", title = "기다리는 형", `when` = "한 달 매매 2번 이하, 큰 변동일에 기다린 비율 60% 이상",  // ios: Data/Calm.swift:179
                 like = "지연을 센다. 목표 100에 닿자 판다."),  // ios: Data/Calm.swift:180
            Kind(id = "seri", title = "원칙형", `when` = "지수 80 이상, 매매의 절반 이상이 규칙대로",  // ios: Data/Calm.swift:181
                 like = "한 박자 두고 관찰과 사실만 말한다."),  // ios: Data/Calm.swift:182
        )
        fun kind(id: String?): Kind? = kinds.firstOrNull { it.id == id }  // ios: Data/Calm.swift:184

        /** 인물 행동 한 줄 (쉬운 말 요약. 정확한 기준은 kinds.when과 match) */
        val does: Map<String, String> = mapOf(  // ios: Data/Calm.swift:187
            "seri" to "대부분 정해 둔 규칙대로 사고판다",  // ios: Data/Calm.swift:188
            "sio" to "거의 안 사고팔고, 크게 흔들리면 기다린다",  // ios: Data/Calm.swift:189
            "seonbae" to "오르면 조금 덜어내며 거꾸로 생각한다",  // ios: Data/Calm.swift:190
            "ir" to "오르면 더 사고, 내리면 바로 판다",  // ios: Data/Calm.swift:191
            "sua" to "점수가 한 달 새 눈에 띄게 좋아지는 중",  // ios: Data/Calm.swift:192
        )
        /** 기록마다 왜 그 점수인지 한 줄 (점수표 points와 같은 뜻) */
        // ios: Data/Calm.swift:195,196
        fun reason(a: Act): String = when (a) {
            Act.rule -> "규칙대로"  // ios: Data/Calm.swift:197
            Act.wait -> "급변일 기다림"  // ios: Data/Calm.swift:198
            Act.chase -> ""  // ios: Data/Calm.swift:199
            Act.switchOut -> "내린 뒤 매도"  // ios: Data/Calm.swift:200
            Act.avgDown, Act.trim -> "규칙 밖"  // ios: Data/Calm.swift:201
            Act.fresh -> "새 종목 · 기록만"  // ios: Data/Calm.swift:202
        }

        /** 지수가 무슨 뜻인지 한 줄 (100 = 직전 수익·손실과 무관하게 매매) */
        // ios: Data/Calm.swift:207
        fun meaning(s: CalmStats): String {
            if (s.n < minEvents) return "기록 ${minEvents}건이 모이면 지수가 나와요."  // ios: Data/Calm.swift:208
            val i = s.index  // ios: Data/Calm.swift:209
            val t = if (i >= 80) "직전 수익·손실에 거의 끌리지 않아요" else if (i >= 60) "직전 결과에 조금 끌려요" else if (i >= 40) "직전 결과에 꽤 끌려요" else "직전 결과에 많이 끌려요"  // ios: Data/Calm.swift:210
            return "${t}. 100이면 결과와 상관없이 매매해요. (매매 ${s.trades}건 기준)"  // ios: Data/Calm.swift:211
        }

        /** 왜 이 유형인지: 매칭에 쓴 숫자를 그대로 보여 준다 */
        // ios: Data/Calm.swift:215
        fun why(id: String?, s: CalmStats): String? {
            val p = { x: Double -> "${(x * 100).rnd().toInt()}%" }  // ios: Data/Calm.swift:216
            return when (id) {  // ios: Data/Calm.swift:217
                "ir" -> "수익 뒤 추가 매수 ${p(s.chase)}, 손실 뒤 매도 ${p(s.switchR)}. 어느 한쪽이 60%를 넘으면 이 유형이에요."  // ios: Data/Calm.swift:218
                "sua" -> { val tr = s.trend?.let { (if (it >= 0) "+" else "") + "$it" } ?: "–"; "최근 30일 지수가 그 전 30일보다 ${tr} 움직였어요. 10 이상 오르면 이 유형이에요." }  // iOS Calm.swift 219줄 (port_check 예외: 따옴표 겹침)
                "seonbae" -> "매매의 ${p(s.contra)}가 수익 뒤 덜어내기처럼 결과와 반대로 한 매매예요. 절반 이상이고 지수 60 이상이면 이 유형이에요."  // ios: Data/Calm.swift:220
                "sio" -> { val pm = cformat("%.1f", s.perMonth); "한 달 매매 ${pm}번, 큰 변동일에 기다린 비율 ${p(s.waitR)}. 매매가 적고 잘 기다리면 이 유형이에요." }  // iOS Calm.swift 221줄 (port_check 예외: 따옴표 겹침)
                "seri" -> "매매의 ${p(s.ruleR)}가 규칙대로예요. 지수가 높고 규칙대로가 많으면 이 유형이에요."  // ios: Data/Calm.swift:222
                else -> null  // ios: Data/Calm.swift:223
            }
        }

        /** 매매 직후 한 줄 피드백 (말하는 사람 = 매칭된 인물, 없으면 홈 친구) */
        // ios: Data/Calm.swift:263
        fun feedback(who: String, a: Act): String {
            Lines.pick("fb.${a.name}.${who}")?.let { return it }  // ios: Data/Calm.swift:264
            val lines: Map<String, Map<Act, String>> = mapOf(  // ios: Data/Calm.swift:265
                "seri" to mapOf(Act.rule to "계획 쪽으로 옮겼어요. 기록했어요.", Act.chase to "오른 종목을 더 샀어요. 계획 신호는 없었어요.",  // ios: Data/Calm.swift:266
                         Act.switchOut to "내린 종목을 팔았어요. 계획 신호는 없었어요.", Act.avgDown to "내린 종목을 더 샀어요. 점수는 그대로예요.",  // ios: Data/Calm.swift:267
                         Act.trim to "오른 종목을 덜었어요. 점수는 그대로예요.", Act.wait to "크게 움직인 날, 아무것도 안 했어요."),  // ios: Data/Calm.swift:268
                "sio" to mapOf(Act.rule to "계획대로. 됐어.", Act.chase to "올라서 더 샀네. 계획엔 없었어.", Act.switchOut to "내려서 팔았네. 하루만 더 셀 걸.",  // ios: Data/Calm.swift:269
                        Act.avgDown to "내려서 더 샀네. 세어 둘게.", Act.trim to "올라서 덜었네. 세어 둘게.", Act.wait to "흔들린 날, 그냥 셌어. 그거면 돼."),  // ios: Data/Calm.swift:270
                "seonbae" to mapOf(Act.rule to "계획대로 했네. 그 계획, 왜 그렇게 정했는지 기억나?", Act.chase to "올라서 더 산 거야, 계획이라서 산 거야?",  // ios: Data/Calm.swift:271
                            Act.switchOut to "내려서 판 거야, 아니면 정말 네 생각이야?", Act.avgDown to "더 산 이유가 가격이야, 계획이야?",  // ios: Data/Calm.swift:272
                            Act.trim to "덜어낸 이유, 한 줄로 말할 수 있어?", Act.wait to "아무것도 안 한 오늘, 어땠어?"),  // ios: Data/Calm.swift:273
                "ir" to mapOf(Act.rule to "멈출 데서 멈췄어. 넘치지 않았어.", Act.chase to "빨랐어. 나도 그랬어.", Act.switchOut to "급하게 돌아섰네. 나도 그랬어.",  // ios: Data/Calm.swift:274
                       Act.avgDown to "더 붙잡았구나. 넘치기 전에 봐.", Act.trim to "덜어냈구나. 무게가 가벼워졌어.", Act.wait to "넘치기 전에 멈춘 한 번이야."),  // ios: Data/Calm.swift:275
                "sua" to mapOf(Act.rule to "제때 했어요.", Act.chase to "말이 먼저 왔네요. 다음엔 한 박자 쉬어요.", Act.switchOut to "먼저 돌아섰네요. 다음엔 한 박자 쉬어요.",  // ios: Data/Calm.swift:276
                        Act.avgDown to "더 샀네요. 점수는 그대로예요.", Act.trim to "덜었네요. 점수는 그대로예요.", Act.wait to "오늘은 침묵을 골랐어요."),  // ios: Data/Calm.swift:277
            )
            return lines[who]?.get(a) ?: lines["seri"]?.get(a) ?: "기록했어요."  // ios: Data/Calm.swift:279
        }

        // ios: Data/Calm.swift:316 습관 사다리: Swift extension Calm 은 이 클래스·companion 안에
        val steps: Map<String, List<Step>> = mapOf(  // ios: Data/Calm.swift:318
            "ir" to listOf(Step(name = "넘치기 전 멈춤", what = "큰 변동일(±5%)에 매매 없이 지나간 날 3번"),  // ios: Data/Calm.swift:319
                   Step(name = "쫓기 줄이기", what = "수익 뒤 추가 매수·손실 뒤 매도가 매매의 40% 미만, 또는 30일 동안 0번"),  // ios: Data/Calm.swift:320
                   Step(name = "지수 +10", what = "최근 30일 지수가 그 전 30일보다 10 이상")),  // ios: Data/Calm.swift:321
            "sua" to listOf(Step(name = "오른 지수 지키기", what = "30일 동안 수익 뒤 추가 매수·손실 뒤 매도 0번"),  // ios: Data/Calm.swift:322
                    Step(name = "규칙대로 3번", what = "규칙 신호(비중 이탈·그달 첫 적립)에 맞춘 매매 3번"),  // ios: Data/Calm.swift:323
                    Step(name = "지수 70 두 달", what = "평정 지수 70 이상을 60일 지키기")),  // ios: Data/Calm.swift:324
            "seonbae" to listOf(Step(name = "역발상을 규칙 안에서", what = "덜어내기·물타기 같은 역방향 매매 중 절반 이상을 규칙 신호에 맞추기 (2건 이상)"),  // ios: Data/Calm.swift:325
                        Step(name = "물타기 쉬기", what = "30일 동안 규칙 밖 물타기 0번"),  // ios: Data/Calm.swift:326
                        Step(name = "규칙대로 절반", what = "걸음 시작 뒤 매매 절반 이상이 규칙대로, 지수 80 이상")),  // ios: Data/Calm.swift:327
            "sio" to listOf(Step(name = "흔들림 지키기", what = "30일 동안 큰 변동일에 기다린 비율 60% 이상"),  // ios: Data/Calm.swift:328
                    Step(name = "기다리다 놓치지 않기", what = "규칙 신호가 왔을 때 규칙대로 1번 실행"),  // ios: Data/Calm.swift:329
                    Step(name = "규칙대로 절반", what = "걸음 시작 뒤 매매 절반 이상이 규칙대로, 지수 80 이상")),  // ios: Data/Calm.swift:330
            "seri" to listOf(Step(name = "한 달 유지", what = "평정 지수 80 이상을 30일"),  // ios: Data/Calm.swift:331
                     Step(name = "석 달 유지", what = "평정 지수 80 이상을 90일"),  // ios: Data/Calm.swift:332
                     Step(name = "여섯 달 유지", what = "평정 지수 80 이상을 180일")),  // ios: Data/Calm.swift:333
        )

        /** 지수 기준 칸 (소식의 위·아래 판단용. 화면에 순위로 쓰지 않는다) */
        fun rank(id: String?): Int = mapOf("ir" to 0, "sua" to 1, "seonbae" to 2, "sio" to 2, "seri" to 3)[id ?: ""] ?: -1  // ios: Data/Calm.swift:337
        fun holdLine(id: String): Int = if (id == "sua") 70 else 80  // ios: Data/Calm.swift:338

        // ios: Data/Calm.swift:340
        private fun days(from: String, to: String): Int {
            val a = Day.date(from) ?: return 0; val b = Day.date(to) ?: return 0  // ios: Data/Calm.swift:341
            return max(0, ((b.epochSecond - a.epochSecond).toDouble() / 86400).rnd().toInt())  // ios: Data/Calm.swift:342
        }
        private fun pct(x: Double): String = "${(x * 100).rnd().toInt()}%"  // ios: Data/Calm.swift:344

        /** 걸음 완료 한 줄 (인물 말투, 대사 데이터 ladder.<유형>.<걸음> 이 있으면 그것) */
        // ios: Data/Calm.swift:444
        fun stepLine(type: String, step: Int, who: String): String {
            (Lines.pick("ladder.${type}.${step}.${who}") ?: Lines.pick("ladder.${type}.${step}"))?.let { return it }  // ios: Data/Calm.swift:445
            val nm = steps[type]?.get(step - 1)?.name ?: ""; return "걸음 ${step}/3 ‘${nm}’ 완료!"  // iOS Calm.swift 446줄 (port_check 예외: 따옴표 겹침)
        }
    }

    // ios: Data/Calm.swift:62
    fun save() { runCatching { NJson.encodeToString(serializer(), this) }.getOrNull()?.let { d -> Defaults.prefs.edit().putString(key, d).apply() } }

    /** 기록 하나 더하기: 플러스 점수는 주당 상한까지만 (과잉 매매로 점수를 쌓지 못하게) */
    // ios: Data/Calm.swift:80
    fun record(e0: CalmEvent) {
        val e = e0.copy()  // ios: Data/Calm.swift:81
        e.pts = points(e.act)  // ios: Data/Calm.swift:82
        if (e.pts > 0) {  // ios: Data/Calm.swift:83
            val w = week(e.day)  // ios: Data/Calm.swift:84
            val got = events.filter { week(it.day) == w && it.pts > 0 }.fold(0) { a, x -> a + x.pts }  // ios: Data/Calm.swift:85
            e.pts = max(0, min(e.pts, weekCap - got))  // ios: Data/Calm.swift:86
        }
        // 같은 날 같은 종목을 기다렸다고 적어 뒀다가 매매했으면 기다림은 지운다
        if (e.isTrade) events = events.filterNot { it.act == Act.wait && it.day == e.day && it.sym == e.sym }  // ios: Data/Calm.swift:89
        events = events + e  // ios: Data/Calm.swift:90
        if (events.size > keep) events = events.drop(events.size - keep)  // ios: Data/Calm.swift:91
        refreshType()  // ios: Data/Calm.swift:92
        climb()  // ios: Data/Calm.swift:93
    }

    /** 앱을 연 날: 지금 인물과 함께한 날을 하나 센다 */
    // ios: Data/Calm.swift:97
    fun touch(today: String = Day.today) {
        refreshType(today)  // ios: Data/Calm.swift:98
        climb(today)  // ios: Data/Calm.swift:99
        if (type != null && together.lastOrNull() != today) together = together + today  // ios: Data/Calm.swift:100
    }

    /** 유형 정하기: 처음 10건이 쌓이면 바로, 그 뒤로는 달이 바뀔 때만 다시 본다 */
    // ios: Data/Calm.swift:104
    fun refreshType(today: String = Day.today) {
        val month = today.take(7)  // ios: Data/Calm.swift:105
        val c = match(stats(today)) ?: return  // ios: Data/Calm.swift:106
        if (type == null) { type = c; typeMonth = month; return }  // ios: Data/Calm.swift:107
        if (typeMonth == month) return  // ios: Data/Calm.swift:108
        typeMonth = month  // ios: Data/Calm.swift:109
        if (c != type) {  // ios: Data/Calm.swift:110
            val up: Boolean? = if (rank(c) == rank(type)) null else rank(c) > rank(type)  // ios: Data/Calm.swift:111
            note(CalmNote(kind = "calm.type.changed", type = c, day = today, from = type, up = up))  // ios: Data/Calm.swift:112
            prevType = type; type = c; together = emptyList()  // ios: Data/Calm.swift:113
        }
    }

    /** 애착 단계: 함께한 날 14일 미만 1, 60일 미만 2, 그 뒤 3 */
    val stage: Int get() = if (together.size < 14) 1 else if (together.size < 60) 2 else 3  // ios: Data/Calm.swift:118

    // ios: Data/Calm.swift:132
    fun stats(today: String = Day.today): CalmStats {
        val s = CalmStats()  // ios: Data/Calm.swift:133
        val t = events.filter { it.isTrade }  // ios: Data/Calm.swift:134
        s.n = events.size; s.trades = t.size  // ios: Data/Calm.swift:135
        s.index = index(events).rnd().toInt()  // ios: Data/Calm.swift:136
        val w = t.filter { it.win }; val l = t.filter { !it.win }  // ios: Data/Calm.swift:137
        s.chase = if (w.isEmpty()) 0.0 else w.count { it.buy && it.act != Act.rule }.toDouble() / w.size.toDouble()  // ios: Data/Calm.swift:138
        s.switchR = if (l.isEmpty()) 0.0 else l.count { !it.buy && it.act != Act.rule }.toDouble() / l.size.toDouble()  // ios: Data/Calm.swift:139
        val waits = events.count { it.act == Act.wait }; val bigTrades = t.count { it.big }  // ios: Data/Calm.swift:140
        s.waitR = if (waits + bigTrades == 0) 0.0 else waits.toDouble() / (waits + bigTrades).toDouble()  // ios: Data/Calm.swift:141
        if (t.isNotEmpty()) {  // ios: Data/Calm.swift:142
            s.ruleR = t.count { it.act == Act.rule }.toDouble() / t.size.toDouble()  // ios: Data/Calm.swift:143
            s.contra = t.count { it.act == Act.trim || (it.act == Act.rule && it.buy != it.win) }.toDouble() / t.size.toDouble()  // ios: Data/Calm.swift:144
        }
        val ago = { d: Int -> Day.str((Day.date(today) ?: Instant.now()).minusSeconds(d.toLong() * 86400)) }  // ios: Data/Calm.swift:146
        val d30 = ago(30); val d60 = ago(60); val d90 = ago(90)  // ios: Data/Calm.swift:147
        s.perMonth = t.count { it.day > d90 }.toDouble() / 3  // ios: Data/Calm.swift:148
        val recent = events.filter { it.day > d30 }; val before = events.filter { it.day > d60 && it.day <= d30 }  // ios: Data/Calm.swift:149
        if (recent.count { it.isTrade } >= 5 && before.count { it.isTrade } >= 5) {  // ios: Data/Calm.swift:150
            s.trend = (index(recent) - index(before)).rnd().toInt()  // ios: Data/Calm.swift:151
        }
        val wk = week(today)  // ios: Data/Calm.swift:153
        s.weekPts = events.filter { week(it.day) == wk }.fold(0) { a, x -> a + x.pts }  // ios: Data/Calm.swift:154
        return s  // ios: Data/Calm.swift:155
    }

    /** 함께한 날과 말투 단계 (14일·60일에 말투가 가까워진다) */
    // ios: Data/Calm.swift:228
    val stageLine: String get() {
        val d = together.size  // ios: Data/Calm.swift:229
        return if (d < 14) "함께한 날 ${d}일 · ${14 - d}일 더 함께하면 말투가 가까워져요"  // ios: Data/Calm.swift:230
            else if (d < 60) "함께한 날 ${d}일 · ${60 - d}일 더 함께하면 말투가 한 번 더 가까워져요"  // ios: Data/Calm.swift:231
            else "함께한 날 ${d}일 · 말투가 가장 가까워졌어요"  // ios: Data/Calm.swift:232
    }

    /** 홈 카드 한 줄: 인물 말투가 애착 단계를 따라 바뀐다 (세리는 기계 문장 → 합쇼체 → 해요체) */
    // ios: Data/Calm.swift:236
    fun say(s: CalmStats, who: String = "seri"): String {
        val n = s.index; val d = together.size  // ios: Data/Calm.swift:237
        // 대사 데이터(Lines)에서 시세가 갱신될 때마다 다른 줄. 없으면 아래 원래 문장
        val vars = mapOf("n" to "$n", "d" to "$d", "k" to "${s.n}", "min" to "${minEvents}")  // ios: Data/Calm.swift:239
        val t = type; if (t != null) { Lines.pick("calm.${min(3, max(1, stage))}.${t}", vars)?.let { return it } }  // ios: Data/Calm.swift:240
        if (type == null) { Lines.pick("calm.none.${who}", vars)?.let { return it } }  // ios: Data/Calm.swift:241
        val k = type ?: ""; val st = stage; return when {  // ios: Data/Calm.swift:242
            k == "" -> "아직 모르겠습니다. 기록 ${s.n}/${minEvents}."  // ios: Data/Calm.swift:243
            k == "seri" && st == 1 -> "관찰 중. 평정 지수 ${n}."  // ios: Data/Calm.swift:244
            k == "seri" && st == 2 -> "평정 지수는 ${n}입니다. 계속 보겠습니다."  // ios: Data/Calm.swift:245
            k == "seri" -> "평정 지수 ${n}이에요. 같이 봐 와서 알아요."  // ios: Data/Calm.swift:246
            k == "sio" && st == 1 -> "${n}. 세어 둘게."  // ios: Data/Calm.swift:247
            k == "sio" && st == 2 -> "${n}이야. 어제보다 하루 더 셌어."  // ios: Data/Calm.swift:248
            k == "sio" -> "${n}. 같이 센 날이 ${d}일이야."  // ios: Data/Calm.swift:249
            k == "seonbae" && st == 1 -> "평정 지수 ${n}. 너는 이 숫자를 어떻게 생각해?"  // ios: Data/Calm.swift:250
            k == "seonbae" && st == 2 -> "${n}이네. 지난달의 너라면 뭘 했을까?"  // ios: Data/Calm.swift:251
            k == "seonbae" -> "${n}까지 왔네. 그게 정말 네 생각이었지?"  // ios: Data/Calm.swift:252
            k == "ir" && st == 1 -> "${n}. 나도 빨랐어."  // ios: Data/Calm.swift:253
            k == "ir" && st == 2 -> "${n}. 넘치기 전에 멈춘 날이 늘고 있어."  // ios: Data/Calm.swift:254
            k == "ir" -> "${n}. 너는 나처럼 되지 않았어."  // ios: Data/Calm.swift:255
            k == "sua" && st == 1 -> "${n}이에요. 아직 고르는 중이에요."  // ios: Data/Calm.swift:256
            k == "sua" && st == 2 -> "${n}이에요. 제때 오는 게 느껴져요."  // ios: Data/Calm.swift:257
            else -> "${n}이에요. 또 올게요. 약속은 아니에요."  // ios: Data/Calm.swift:258
        }
    }

    /** 지금 걸음의 진행 */
    // ios: Data/Calm.swift:347
    fun progress(today: String = Day.today): StepProgress? {
        val l = ladder ?: return null; if (l.step !in 1..3) return null  // ios: Data/Calm.swift:348
        val ev = events.filter { it.day >= l.since }; val t = ev.filter { it.isTrade }  // ios: Data/Calm.swift:349
        val bad = t.filter { it.act == Act.chase || it.act == Act.switchOut }  // ios: Data/Calm.swift:350
        val s = stats(today); val held = l.hold?.let { days(it, today) } ?: 0  // ios: Data/Calm.swift:351
        // 마지막으로 어긴 날(없으면 걸음 시작일)부터 지켜 온 날
        val streak = { broke: List<CalmEvent> -> days(broke.map { it.day }.maxOrNull() ?: l.since, today) }  // ios: Data/Calm.swift:353
        val half = { ->  // ios: Data/Calm.swift:354
            val r = if (t.isEmpty()) 0.0 else t.count { it.act == Act.rule }.toDouble() / t.size.toDouble()  // ios: Data/Calm.swift:355
            val ok = t.size >= 2 && r >= 0.5 && s.index >= 80  // ios: Data/Calm.swift:356
            StepProgress(cur = if (ok) 1 else 0, goal = 1, text = "규칙대로 ${pct(r)} · 지수 ${s.index}")  // ios: Data/Calm.swift:357
        }
        val ty = l.type; val st = l.step; return when {  // ios: Data/Calm.swift:359
            ty == "ir" && st == 1 -> {  // ios: Data/Calm.swift:360
                val n = ev.count { it.act == Act.wait }  // ios: Data/Calm.swift:361
                StepProgress(cur = n, goal = 3, text = "멈춤 ${min(n, 3)}/3")  // ios: Data/Calm.swift:362
            }
            ty == "ir" && st == 2 -> {  // ios: Data/Calm.swift:363
                if (t.size >= 3) {  // ios: Data/Calm.swift:364
                    val r = bad.size.toDouble() / t.size.toDouble()  // ios: Data/Calm.swift:365
                    return StepProgress(cur = if (r < 0.4) 1 else 0, goal = 1, text = "쫓은 매매 ${pct(r)} (40% 미만이면 완료)")  // ios: Data/Calm.swift:366
                }
                val d = if (bad.isEmpty()) days(l.since, today) else 0  // ios: Data/Calm.swift:368
                StepProgress(cur = d, goal = 30, text = "쫓지 않은 날 ${min(d, 30)}/30")  // ios: Data/Calm.swift:369
            }
            ty == "ir" && st == 3 -> {  // ios: Data/Calm.swift:370
                val tr = s.trend ?: 0  // ios: Data/Calm.swift:371
                val sg = if (tr >= 0) "+" else ""; StepProgress(cur = max(0, tr), goal = 10, text = if (s.trend == null) "30일씩 매매 5건이 모이면 재요" else "지난 30일보다 ${sg}${tr} (목표 +10)")  // iOS Calm.swift 372줄 (port_check 예외: 따옴표 겹침)
            }
            ty == "sua" && st == 1 -> {  // ios: Data/Calm.swift:373
                val d = streak(bad)  // ios: Data/Calm.swift:374
                StepProgress(cur = d, goal = 30, text = "쫓지 않은 날 ${min(d, 30)}/30")  // ios: Data/Calm.swift:375
            }
            ty == "sua" && st == 2 -> {  // ios: Data/Calm.swift:376
                val n = t.count { it.act == Act.rule }  // ios: Data/Calm.swift:377
                StepProgress(cur = n, goal = 3, text = "규칙대로 ${min(n, 3)}/3")  // ios: Data/Calm.swift:378
            }
            ty == "sua" && st == 3 ->  // ios: Data/Calm.swift:379,380
                StepProgress(cur = held, goal = 60, text = "지수 70 이상 ${min(held, 60)}/60일")
            ty == "seonbae" && st == 1 -> {  // ios: Data/Calm.swift:381
                val c = t.filter { it.act == Act.trim || it.act == Act.avgDown || (it.act == Act.rule && it.buy != it.win) }  // ios: Data/Calm.swift:382
                val r = if (c.isEmpty()) 0.0 else c.count { it.act == Act.rule }.toDouble() / c.size.toDouble()  // ios: Data/Calm.swift:383
                StepProgress(cur = if (c.size >= 2 && r >= 0.5) 1 else 0, goal = 1, text = "역방향 매매 ${c.size}건 중 규칙대로 ${pct(r)}")  // ios: Data/Calm.swift:384
            }
            ty == "seonbae" && st == 2 -> {  // ios: Data/Calm.swift:385
                val d = streak(t.filter { it.act == Act.avgDown })  // ios: Data/Calm.swift:386
                StepProgress(cur = d, goal = 30, text = "물타기 없이 ${min(d, 30)}/30일")  // ios: Data/Calm.swift:387
            }
            ty == "sio" && st == 1 -> {  // ios: Data/Calm.swift:388
                val w = ev.count { it.act == Act.wait }; val b = t.count { it.big }  // ios: Data/Calm.swift:389
                val r = if (w + b == 0) 1.0 else w.toDouble() / (w + b).toDouble()  // ios: Data/Calm.swift:390
                val d = if (r >= 0.6) days(l.since, today) else 0  // ios: Data/Calm.swift:391
                StepProgress(cur = d, goal = 30, text = "기다림 ${pct(r)} · ${min(d, 30)}/30일")  // ios: Data/Calm.swift:392
            }
            ty == "sio" && st == 2 -> {  // ios: Data/Calm.swift:393
                val n = t.count { it.act == Act.rule }  // ios: Data/Calm.swift:394
                StepProgress(cur = n, goal = 1, text = if (n > 0) "규칙대로 1번 했어요" else "규칙 신호가 오면 그때 한 번")  // ios: Data/Calm.swift:395
            }
            (ty == "seonbae" && st == 3) || (ty == "sio" && st == 3) ->  // ios: Data/Calm.swift:396,397
                half()
            ty == "seri" -> {  // ios: Data/Calm.swift:398
                val k = st; val g = listOf(30, 90, 180)[k - 1]  // ios: Data/Calm.swift:399
                StepProgress(cur = held, goal = g, text = "지수 80 이상 ${min(held, g)}/${g}일")  // ios: Data/Calm.swift:400
            }
            else -> null  // ios: Data/Calm.swift:401
        }
    }

    /** 유형이 정해지면 사다리를 맞추고, 걸음이 끝났으면 다음 걸음으로. 소식은 notes에 쌓는다 */
    // ios: Data/Calm.swift:406
    fun climb(today: String = Day.today) {
        val t = type ?: return; if (steps[t] == null) return  // ios: Data/Calm.swift:407
        if (ladder?.type != t) ladder = Ladder(type = t, since = today, done = ladder?.done ?: emptyList())  // ios: Data/Calm.swift:408
        val s = stats(today)  // ios: Data/Calm.swift:409
        val h0 = ladder?.hold  // ios: Data/Calm.swift:410
        ladder = ladder?.copy(hold = if (s.n >= minEvents && s.index >= holdLine(t)) (h0 ?: today) else null)  // ios: Data/Calm.swift:411
        for (k in 0 until 3) {  // ios: Data/Calm.swift:412
            val l = ladder?.copy() ?: break; if (l.step !in 1..3) break; val p = progress(today) ?: break; if (!p.done) break  // ios: Data/Calm.swift:413
            val name = steps[t]!![l.step - 1].name  // ios: Data/Calm.swift:414
            l.done = l.done + Ladder.Done(type = t, step = l.step, day = today)  // ios: Data/Calm.swift:415
            note(CalmNote(kind = "calm.step.done", type = t, day = today, step = l.step, name = name))  // ios: Data/Calm.swift:416
            l.step += 1; l.since = today; l.progCur = 0  // ios: Data/Calm.swift:417
            ladder = l  // ios: Data/Calm.swift:418
        }
        val l = ladder?.copy() ?: return  // ios: Data/Calm.swift:420
        val h = l.hold; if (t == "seri" && l.step > 3 && h != null && days(h, today) >= 180 * (l.keeps + 2)) {  // ios: Data/Calm.swift:421
            l.keeps += 1  // ios: Data/Calm.swift:422
            note(CalmNote(kind = "calm.top.keep", type = t, day = today, months = 6 * (l.keeps + 1)))  // ios: Data/Calm.swift:423
        }
        val p = progress(today); if (p != null && p.cur > l.progCur && l.progDay != today) {  // ios: Data/Calm.swift:425
            l.progCur = p.cur; l.progDay = today  // ios: Data/Calm.swift:426
            note(CalmNote(kind = "calm.step.progress", type = t, day = today, step = l.step, name = steps[t]!![l.step - 1].name, cur = min(p.cur, p.goal), goal = p.goal))  // ios: Data/Calm.swift:427
        }
        ladder = l  // ios: Data/Calm.swift:429
    }

    // ios: Data/Calm.swift:432
    fun note(n: CalmNote) {
        val a = (notes ?: emptyList()).toMutableList()  // ios: Data/Calm.swift:433
        // 같은 걸음의 진행 소식은 마지막 것만
        if (n.kind == "calm.step.progress") a.removeAll { it.kind == n.kind && it.type == n.type && it.step == n.step }  // ios: Data/Calm.swift:435
        a.add(n)  // ios: Data/Calm.swift:436
        notes = a.takeLast(30)  // ios: Data/Calm.swift:437
    }

    /** 홈 리마인드가 소식을 가져가면 비운다 */
    fun takeNotes(): List<CalmNote> { val r = notes ?: emptyList(); notes = null; return r }  // ios: Data/Calm.swift:441
}

// MARK: 습관 사다리
// 유형마다 잴 수 있는 걸음 3개. 걸음은 시작한 날부터 세고, 완료한 걸음은 내려가지 않는다.
// 매매를 더 해야 오르는 걸음은 두지 않는다 (기다림·규칙 신호·지켜 온 날로 오른다).
// 순서는 지수 기준일 뿐, 화면에서 인물 순위로 보여 주지 않는다. 근거: notes/평정-습관-사다리-2026-10-11.md

// ios: Data/Calm.swift:288
@Serializable
data class Ladder(
    var type: String,  // ios: Data/Calm.swift:290
    var step: Int = 1,               // ios: Data/Calm.swift:291 지금 걸음 (1~3). 4 = 이 유형의 걸음을 다 오름
    var since: String,          // ios: Data/Calm.swift:292 지금 걸음을 시작한 날
    var hold: String? = null,    // ios: Data/Calm.swift:293 지수가 기준 이상으로 처음 올라선 날 (내려가면 비운다)
    var keeps: Int = 0,              // ios: Data/Calm.swift:294 세리: 여섯 달 걸음 뒤 더 지킨 반년 수
    var progDay: String? = null, // ios: Data/Calm.swift:295 진행 소식을 마지막으로 낸 날 (하루 한 번)
    var progCur: Int = 0,  // ios: Data/Calm.swift:296
    var done: List<Done> = emptyList(),      // ios: Data/Calm.swift:297 유형이 바뀌어도 남는다
) {
    @Serializable data class Done(var type: String, var step: Int, var day: String)  // ios: Data/Calm.swift:289
}

/** 홈 리마인드가 받는 사다리 소식. kind: calm.step.done · calm.step.progress · calm.type.changed · calm.top.keep */
// ios: Data/Calm.swift:301
@Serializable
data class CalmNote(
    var kind: String,  // ios: Data/Calm.swift:302
    var type: String,  // ios: Data/Calm.swift:303
    var day: String,  // ios: Data/Calm.swift:304
    var step: Int? = null,  // ios: Data/Calm.swift:305
    var name: String? = null,  // ios: Data/Calm.swift:306
    var cur: Int? = null,  // ios: Data/Calm.swift:307
    var goal: Int? = null,  // ios: Data/Calm.swift:308
    var from: String? = null,  // ios: Data/Calm.swift:309
    var up: Boolean? = null,        // ios: Data/Calm.swift:310 유형이 지수 기준으로 위로 옮겼는지 (같은 칸이면 nil)
    var months: Int? = null,  // ios: Data/Calm.swift:311
)

// ios: Data/Calm.swift:314
data class StepProgress(val cur: Int, val goal: Int, val text: String) { val done: Boolean get() = cur >= goal }

// ios: Data/Calm.swift:450 Swift extension AppModel → 아래 확장 함수들. calm 은 값(struct)처럼: copy() 를 고친 뒤 다시 넣어야 저장된다 (didSet → save)

/** 규칙 신호: 회복 루트는 몰린 종목 비중이 계획에서 5%p 넘게 벗어났을 때, 목표 루트는 그달 첫 적립 매수 */
fun AppModel.calmDrift(): Double? = if (isGoal) null else abs(focusWeight - planWeight)  // ios: Data/Calm.swift:452

/** 수량 고치기(직접 고치기로 수량이 바뀐 것 포함)·종목 추가를 저장한 뒤 부른다. 기록한 행동을 돌려준다 */
// ios: Data/Calm.swift:455,456
fun AppModel.calmTrade(sym: Symbol, buy: Boolean, avgBefore: Double, driftBefore: Double?, fresh: Boolean = false): Act {
    val today = Day.today; val win = sym.last >= avgBefore  // ios: Data/Calm.swift:457
    var rule = false  // ios: Data/Calm.swift:458
    val d0 = driftBefore; if (d0 != null && d0 >= 0.05) { val d1 = calmDrift(); if (d1 != null) rule = d1 < d0 }  // ios: Data/Calm.swift:459
    if (isGoal && buy && gM > 0) {  // ios: Data/Calm.swift:460
        val month = today.take(7)  // ios: Data/Calm.swift:461
        rule = !calm.events.any { it.buy && it.day.startsWith(month) }  // ios: Data/Calm.swift:462
    }
    val act: Act = if (rule) Act.rule else if (fresh) Act.fresh else if (win) (if (buy) Act.chase else Act.trim) else (if (buy) Act.avgDown else Act.switchOut)  // ios: Data/Calm.swift:464
    val c = calm.copy(); c.record(CalmEvent(day = today, sym = sym.id, buy = buy, win = win, act = act, big = abs(sym.quote.change) >= 0.05, pts = 0)); calm = c  // ios: Data/Calm.swift:465
    return act  // ios: Data/Calm.swift:466
}

/** 한 번만: 이 기록이 생기기 전 수량 기록(lots)에서 날짜별 수량 변화를 찾아 내 투자 습관에 채운다.
 *  첫 줄은 시작 수량이라 빼고, 이미 있는 날·종목은 건너뛴다. 그날 종가와 지금 평균 단가로 수익·손실을 보고, 종가를 모르면 점수 없는 기록으로 */
// ios: Data/Calm.swift:471
fun AppModel.calmBackfill() {
    val flag = "calm.backfill.v1"; val ud = Defaults.prefs  // ios: Data/Calm.swift:472
    if (isSample || !persists || ud.getBoolean(flag, false)) return  // ios: Data/Calm.swift:473
    ud.edit().putBoolean(flag, true).apply()  // ios: Data/Calm.swift:474
    val add = mutableListOf<CalmEvent>()  // ios: Data/Calm.swift:475
    for ((a, b) in lots.zip(lots.drop(1))) { if (b.d >= Day.today) continue  // ios: Data/Calm.swift:476
        for (t in LinkedHashSet(a.h.keys).apply { addAll(b.h.keys) }) {  // ios: Data/Calm.swift:477
            val q0 = a.h[t] ?: 0.0; val q1 = b.h[t] ?: 0.0  // ios: Data/Calm.swift:478
            val id = Sync.appId(t); if (q0 == q1 || id == null || id == "DRNK" ||  // ios: Data/Calm.swift:479,480
                calm.events.any { it.day == b.d && it.sym == id }) continue
            val buy = q1 > q0; val h = PriceHistory.of(id); val close = h?.on(b.d)  // ios: Data/Calm.swift:481
            val prev = h?.let { hh -> hh.dates.indexOfLast { it < b.d }.takeIf { it >= 0 }?.let { hh.closes[it] } }  // ios: Data/Calm.swift:482
            val big = close?.let { c -> prev?.let { abs(c / it - 1) >= 0.05 } } ?: false  // ios: Data/Calm.swift:483
            val avg = holdings.firstOrNull { it.symbol == id }?.avg  // ios: Data/Calm.swift:484
            val win = close?.let { c -> avg?.let { c >= it } }  // ios: Data/Calm.swift:485
            val act: Act = if (q0 == 0.0) Act.fresh else win?.let { if (it) (if (buy) Act.chase else Act.trim) else (if (buy) Act.avgDown else Act.switchOut) } ?: (if (buy) Act.avgDown else Act.trim)  // ios: Data/Calm.swift:486
            add.add(CalmEvent(day = b.d, sym = id, buy = buy, win = win ?: false, act = act, big = big, pts = Calm.points(act)))  // ios: Data/Calm.swift:487
        }
    }
    if (add.isEmpty()) return  // ios: Data/Calm.swift:490
    val c = calm.copy()  // ios: Data/Calm.swift:491
    c.events = (add + c.events).sortedBy { it.day }.takeLast(Calm.keep)  // ios: Data/Calm.swift:492
    c.refreshType(); c.climb()  // ios: Data/Calm.swift:493
    calm = c  // ios: Data/Calm.swift:494
}

/** 홈을 열 때: 함께한 날을 세고, 내 종목이 ±5% 넘게 움직였는데 오늘 매매가 없으면 기다림 +1 (하루 한 번) */
// ios: Data/Calm.swift:498
fun AppModel.calmOpen() {
    val today = Day.today  // ios: Data/Calm.swift:499
    calmBackfill()  // ios: Data/Calm.swift:500
    recordDay()   // ios: Data/Calm.swift:501 내 기록: 홈을 연 날 한 줄 (같은 날은 지금 값으로 고침)
    val r = if (calm.events.any { it.day == today }) null  // ios: Data/Calm.swift:502,503
        else rows.filter { abs(it.sym.quote.change) >= 0.05 }.maxByOrNull { abs(it.sym.quote.change) }
    if (r != null) { val c = calm.copy(); c.record(CalmEvent(day = today, sym = r.sym.id, buy = false, win = r.value >= r.cost, act = Act.wait, big = true, pts = 0)); calm = c }  // ios: Data/Calm.swift:504
    val c2 = calm.copy(); c2.touch(today); calm = c2  // ios: Data/Calm.swift:506
}

/** 말하는 인물: 매칭된 인물, 없으면 홈 친구. 홈에 내 캐릭터를 두면 내 캐릭터가 (말투는 유형 인물) */
val AppModel.calmSpeaker: String get() = if (homeFriendShown == "me") "me" else calm.type ?: homeFriendShown  // ios: Data/Calm.swift:510
