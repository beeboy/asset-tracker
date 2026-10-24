package com.naeilo.app.ui

import androidx.compose.foundation.background
import com.naeilo.app.core.tr
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.MeChar
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.data.meUnlocked
import com.naeilo.app.data.voice
import com.naeilo.app.ui.me.MeAvatar

// 미션 안내 인물 (캐릭터 설정 스레드 시안 mission-flow-seri*): 미션 제목 아래 회색 안내 문구를 인물 말풍선으로.
// 화면 종류마다 동작이 다르다 — 안내: point, 결과 공개: spread, 퀴즈·결정: think → 맞으면 cheer 틀리면 point,
// 그래프 보기: back, 계획·구성 고르기: side, 정한 뒤: cheer, 입력: stand, 처음 인사: wave.
// 말은 해요체, 사실만 (투자 권유 아님).
// ios: Views/Guide.swift:7
enum class Pose(val raw: String) { stand("stand"), spread("spread"), point("point"), wave("wave"), think("think"), cheer("cheer"), side("side"), back("back"), sideL("side_l") }

// ios: Views/Guide.swift:9
/** 미션 안내 인물: 설정에서 고를 수 있고, 기본(자동)은 처음엔 세리, 그 뒤로는 가장 최근에 만난 인물 */
// ios: Views/Guide.swift:11
val AppModel.guideFriend: String get() {
    if ((guidePick == "me" && meUnlocked) || (guidePick == null && homeFriendShown == "me")) return "me"   // ios: Views/Guide.swift:12
    guidePick?.let { g -> val i = Shelter.friends.indexOfFirst { it.id == g }; if (i >= 0 && friendOn(i)) return g }   // ios: Views/Guide.swift:13
    val open = (0 until Shelter.friends.size).filter { friendOn(it) }   // ios: Views/Guide.swift:14
    return Shelter.friends[open.lastOrNull() ?: 0].id   // ios: Views/Guide.swift:15
}
// ios: Views/Guide.swift:17
var AppModel.guidePick: String?
    get() { guideTick; return Defaults.prefs.getString("guidePick", null) }   // ios: Views/Guide.swift:18
    set(newValue) { Defaults.prefs.edit().putString("guidePick", newValue).apply(); guideTick += 1 }   // ios: Views/Guide.swift:19

/** 동작 그림 하나 (최근접 확대) */
// ios: Views/Guide.swift:24,25,26,27,29
@Composable
fun MotionSprite(friend: String, pose: Pose, height: Dp = 76.dp, modifier: Modifier = Modifier, model: AppModel? = null) {
    val me: MeChar? = model?.me ?: MeChar.current   // ios: Views/Guide.swift:28
    // ios: Views/Guide.swift:30
    if (friend == "me" && me != null) {
        // 내 캐릭터는 동작 그림이 없어 선 모습 하나 (옆을 보는 동작만 뒤집는다)
        // ios: Views/Guide.swift:32,33,34
        Box(modifier.size(height * 240 / 280, height).semantics { hideFromAccessibility() }, contentAlignment = Alignment.Center) {
            MeAvatar(me = me, width = height * 24 / 35, height = height * 34 / 35, modifier = Modifier.graphicsLayer { scaleX = if (pose == Pose.sideL) -1f else 1f; scaleY = 1f })
        }
    } else {   // ios: Views/Guide.swift:35
        Sprite(friend, pose, height, modifier)   // ios: Views/Guide.swift:36
    }
}
// ios: Views/Guide.swift:39,40,41
@Composable
private fun Sprite(friend: String, pose: Pose, height: Dp, modifier: Modifier) =
    PixelN("mo_${friend}_${pose.raw}", width = height * (240.0f / 280.0f), height = height, modifier = modifier.semantics { hideFromAccessibility() })

/** 미션 말풍선: 인물 + 이름(+ 동작 설명) + 말 */
// ios: Views/Guide.swift:46,47,48,49,51
@Composable
fun GuideBubble(m: AppModel, pose: Pose, text: String,
                note: String? = null) {   // ios: Views/Guide.swift:50 "그래프 쪽을 돌아봄" 처럼 이름 옆 작은 글
    m.guideTick   // ios: Views/Guide.swift:52
    val f = m.guideFriend; val name = m.friendName(f)   // ios: Views/Guide.swift:53
    val said = Voice.say(text, m.voice(f), pose)   // ios: Views/Guide.swift:54
    // ios: Views/Guide.swift:55,66,67
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = tr("$name: $said") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(66.dp), contentAlignment = Alignment.Center) { MotionSprite(friend = f, pose = pose, modifier = Modifier.charIdle(), model = m) }   // ios: Views/Guide.swift:56
        // ios: Views/Guide.swift:57,61,62,63,64
        Column(
            Modifier.weight(1f).background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            T(name + (note?.let { " · " + it } ?: ""), style = appFont(12f, FontWeight.SemiBold), color = Theme.blue)   // ios: Views/Guide.swift:58
            T(said, style = appFont(14f).ls(2f), color = Theme.ink)   // ios: Views/Guide.swift:59
        }
    }
}

/** 인물별 말투. 문구(숫자·안내·면책)는 그대로 두고 말끝과 한마디만 바꾼다 (캐릭터 설정 스레드의 위젯 대사 기준)
 *  세리: 해요체, "……" 한 박자 (원문 그대로) · 시오: 짧은 반말, 센다 · 선배: 반말, 되묻는다
 *  이르: 차분한 반말, 서두르지 말라고 · 수아: 공손한 해요체, 제때 */
// ios: Views/Guide.swift:74
object Voice {
    // ios: Views/Guide.swift:75
    private val banmal: List<Pair<String, String>> = listOf(
        "하시는군요." to "하는구나.", "드리지 않아요" to "주지 않아", "하셔야 해요" to "해야 해", "같이 봐요." to "같이 보자.",   // ios: Views/Guide.swift:76
        "정해요." to "정하자.", "싶나요?" to "싶어?", "아니에요" to "아니야", "이에요" to "이야", "예요" to "야",   // ios: Views/Guide.swift:77
        "보세요" to "봐", "넣으세요" to "넣어", "주세요" to "줘",   // ios: Views/Guide.swift:78
    )
    private val ending = Regex("(어|아|해|여|와|워|꿔|춰|써|봐|돼|줘|져|려|내|가|서)요([.?!,]|$)")   // ios: Views/Guide.swift:80
    // ios: Views/Guide.swift:81
    private val pre: Map<String, Map<Pose, String>> = mapOf(
        "sio" to mapOf(Pose.wave to "왔네. ", Pose.point to "짧게. ", Pose.spread to "셌어. "),   // ios: Views/Guide.swift:82
        "seonbae" to mapOf(Pose.wave to "왔어? "),   // ios: Views/Guide.swift:83
        "ir" to mapOf(Pose.think to "서두르지 마. ", Pose.spread to "흔들려도 괜찮아. ", Pose.wave to "천천히 하자. ", Pose.side to "천천히 골라. "),   // ios: Views/Guide.swift:84
        "sua" to mapOf(Pose.wave to "제때 오셨어요. ", Pose.point to "순서대로 볼게요. "),   // ios: Views/Guide.swift:85
    )
    // ios: Views/Guide.swift:87
    private val suf: Map<String, Map<Pose, String>> = mapOf(
        "seonbae" to mapOf(Pose.think to " 너라면 뭘 고를 것 같아?", Pose.spread to " 왜 여기까지 왔다고 생각해?", Pose.back to " 어디가 제일 눈에 띄어?", Pose.side to " 어느 쪽이 마음 편해?"),   // ios: Views/Guide.swift:88
    )
    // ios: Views/Guide.swift:90
    fun say(t0: String, f: String, pose: Pose): String {
        if (f == "seri") return t0   // ios: Views/Guide.swift:91
        var t = t0.replace("…… ", "").replace("……", "").trim(' ')   // ios: Views/Guide.swift:92
        val decided = pose == Pose.cheer && t.startsWith("정했")   // ios: Views/Guide.swift:93
        // ios: Views/Guide.swift:94
        if (f == "sio" || f == "seonbae" || f == "ir") {
            for ((a, b) in banmal) t = t.replace(a, b)   // ios: Views/Guide.swift:95
            t = ending.replace(t, "$1$2")   // ios: Views/Guide.swift:96 (withTemplate: "$1$2")
        }
        var head = pre[f]?.get(pose) ?: ""; var tail = suf[f]?.get(pose) ?: ""   // ios: Views/Guide.swift:98
        // 계획을 정한 뒤: 선배는 이유를 적게 하고, 이르는 흔들려도 서두르지 말라고, 수아는 기록한다고
        // ios: Views/Guide.swift:100
        if (decided) {
            // ios: Views/Guide.swift:101
            when (f) {
                "seonbae" -> tail = " 왜 그렇게 정했는지 한 줄 적어 둬."   // ios: Views/Guide.swift:102
                "ir" -> tail = " 이제 흔들려도 서두르지 마."   // ios: Views/Guide.swift:103
                "sua" -> head = "좋아요, 기록해 둘게요. "   // ios: Views/Guide.swift:104
                "sio" -> head = "됐어. "   // ios: Views/Guide.swift:105
                else -> {}   // ios: Views/Guide.swift:106
            }
        }
        return head + t + tail   // ios: Views/Guide.swift:109
    }
}

/** 설정 > 함께할 인물: 한 줄(그림·이름·바꾸기) + 자동 토글. 토글 = 예전 "자동" 칩, 끄면 고른 인물로 고정 */
// ios: Views/Guide.swift:114,115,116
@Composable
fun GuidePickRow(m: AppModel, compact: Boolean = false) {   // ios: Views/Guide.swift:116 compact: 설정 첫 화면, 인물을 누르면 바로 고르는 메뉴(자동 교체도 그 안에)
    m.guideTick   // ios: Views/Guide.swift:117
    // ios: Views/Guide.swift:118,119
    val opts: List<Pair<String, String>> = Shelter.friends.withIndex().filter { m.friendOn(it.index) }.map { it.value.id to it.value.name } +
        (if (m.meUnlocked) listOf("me" to m.friendName("me")) else emptyList())
    // ios: Views/Guide.swift:122,123,124,125,126,127,128
    if (compact) {
        val auto = m.guidePick == null
        DropMenu(opts.map { (id, name) -> MenuItem(name, onClick = { m.guidePick = id }) } +
            MenuItem("새 친구 만나면 자동 교체", icon = if (auto) "checkmark" else null, onClick = { m.guidePick = if (auto) m.guideFriend else null })) {
            // ios: Views/Guide.swift:130,139
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MotionSprite(friend = m.guideFriend, pose = Pose.wave, height = 48.dp, modifier = Modifier.charIdle(2), model = m)   // ios: Views/Guide.swift:131
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(m.friendName(m.guideFriend), style = appFont(16f, FontWeight.Bold), color = Theme.ink)   // ios: Views/Guide.swift:133
                    T("눌러서 함께할 인물 바꾸기", style = appFont(12f), color = Theme.sub)   // ios: Views/Guide.swift:134
                }
            }
        }
        return
    }
    // ios: Views/Guide.swift:142
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ios: Views/Guide.swift:121
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MotionSprite(friend = m.guideFriend, pose = Pose.wave, height = 48.dp, modifier = Modifier.charIdle(2), model = m)   // ios: Views/Guide.swift:122
            // ios: Views/Guide.swift:123
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(m.friendName(m.guideFriend), style = appFont(15f, FontWeight.Bold))   // ios: Views/Guide.swift:124
                T("홈·미션에서 말 걸어요", style = appFont(12f), color = Theme.sub)   // ios: Views/Guide.swift:125
            }
            // ios: Views/Guide.swift:127,128,129,130,132 (Spacer 는 앞 칸 weight)
            DropMenu(opts.map { (id, name) -> MenuItem(name, onClick = { m.guidePick = id }) }) {
                // ios: Views/Guide.swift:133
                Box(Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp), contentAlignment = Alignment.Center) { T("바꾸기", style = appFont(14f, FontWeight.Bold), color = Theme.teal) }
            }
        }
        HDivider(Theme.line)   // ios: Views/Guide.swift:136
        // ios: Views/Guide.swift:137,142 (44)
        ToggleRow(m.guidePick == null, { v -> m.guidePick = if (v) null else m.guideFriend }, Modifier.defaultMinSize(minHeight = 44.dp), tint = Theme.teal) {
            // ios: Views/Guide.swift:138
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T("새 친구 만나면 자동 교체", style = appFont(14f, FontWeight.SemiBold))   // ios: Views/Guide.swift:139
                T("처음 세리 → 최근 만난 인물", style = appFont(12f), color = Theme.sub)   // ios: Views/Guide.swift:140
            }
        }
    }
}

