package com.naeilo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.naeilo.app.core.Engine
import com.naeilo.app.core.Store
import com.naeilo.app.core.WChar
import com.naeilo.app.core.WL
import com.naeilo.app.core.WReward
import kotlinx.coroutines.launch
import java.time.Instant

// 인물 보상 위젯: 모두 작은 크기 8개. 확정 시안 B(자산 언덕 · 본전 사다리 · 블록 탑 · 전광판) + 키 재기 · 퍼즐 · 꽃밭 · 줄다리기
// 위젯마다 인물을 고른다 (위젯을 놓을 때·길게 눌러 다시 설정할 때 CharConfig 화면). 누르면 앱의 쉼터로 간다.
// 앱 설정의 시안 조작 > 인물 위젯 미리보기도 같은 그리기(CharViews.kt)를 쓴다.
// 기본은 인물 색 그라데이션 바탕, 다크 모드는 #1c1c1f 바탕에 인물 색은 빛·제목·막대·블록·말풍선 테두리에만.

// ios: Widgets/CharWidgets.swift:10
enum class WhoEnum(val raw: String) {
    // 'seri' 는 '홈 인물 따라가기'가 생기기 전 위젯의 기본값이었다. 예전 값 'seri' 는 홈 인물 따라가기로 읽고,
    // 세리를 고정하고 싶을 때는 새 값 'seri.pin' 을 쓴다 (고르는 목록에는 legacy 가 안 보인다)
    // ios: Widgets/CharWidgets.swift:14
    home("home"), legacy("seri"), seri("seri.pin"), sio("sio"), seonbae("seonbae"), ir("ir"), sua("sua"), me("me");

    // ios: Widgets/CharWidgets.swift:16,17,18
    val display: String get() = WL.t(caseDisplayRepresentations[this] ?: raw)
    // ios: Widgets/CharWidgets.swift:21
    val follows: Boolean get() = this == home || this == legacy
    // ios: Widgets/CharWidgets.swift:22
    val charId: String get() = if (this == seri) "seri" else raw

    companion object {
        /** 타입 이름 "인물" */
        // ios: Widgets/CharWidgets.swift:15
        val typeDisplayRepresentation: String get() = WL.t("인물")
        /** 고르는 목록에 보이는 이름 */
        // ios: Widgets/CharWidgets.swift:16,17,18
        val caseDisplayRepresentations: Map<WhoEnum, String> by lazy { mapOf(home to "앱 홈의 인물 따라가기", legacy to "앱 홈의 인물 따라가기",
            seri to "세리", sio to "시오", seonbae to "선배", ir to "이르", sua to "수아", me to "내 캐릭터") }
        // ios: Widgets/CharWidgets.swift:20
        val choices: List<WhoEnum> = listOf(home, seri, sio, seonbae, ir, sua, me)
        fun of(raw: String?): WhoEnum = entries.firstOrNull { it.raw == raw } ?: home
    }
}

// ios: Widgets/CharWidgets.swift:25
object WhoOptions {
    /** 만난 인물만 고를 수 있다 (2026-10-16 결정: 못 만난 친구 미리 보기는 뺌) */
    // ios: Widgets/CharWidgets.swift:27
    fun results(): List<WhoEnum> {
        // ios: Widgets/CharWidgets.swift:28
        val on = Store.read(WReward.serializer(), "reward.json")?.friendsOn
        // ios: Widgets/CharWidgets.swift:29
        return WhoEnum.choices.filter { it == WhoEnum.home || it == WhoEnum.seri || on?.contains(it.charId) ?: true }
    }
}

/** 인물 고르기 (아이폰 WidgetConfigurationIntent): 위젯을 놓을 때·길게 눌러 다시 설정할 때 뜨는 화면 */
// ios: Widgets/CharWidgets.swift:33
class WhoIntent : ComponentActivity() {
    companion object {
        // ios: Widgets/CharWidgets.swift:34
        val title: String get() = WL.t("인물 고르기")
        // ios: Widgets/CharWidgets.swift:35
        val description: String get() = WL.t("위젯에 나올 인물을 골라요. 아직 못 만난 인물은 실루엣으로 보여요.")
        /** 고르는 값의 이름과 기본값 (아이폰 @Parameter) */
        // ios: Widgets/CharWidgets.swift:36
        const val whoTitle = "인물"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val wid = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 고르지 않고 나가면 위젯을 놓지 않는다 (안드로이드 설정 화면 규칙). 기본은 홈 인물
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val dark = isDark(this)
        val fg = if (dark) Color.White else Color(0xFF1B2230)
        setContent {
            Column(Modifier.fillMaxSize().background(if (dark) Color(0xFF15181F) else Color(0xFFF2F3F7)).safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(WhoIntent.title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fg))
                BasicText(WhoIntent.description, Modifier.padding(bottom = 12.dp), style = TextStyle(fontSize = 14.sp, color = fg.copy(alpha = 0.7f)))
                for (w in WhoOptions.results()) {   // @Parameter(title: "인물", default: .home)
                    BasicText(w.display, Modifier.fillMaxWidth().clickable { pick(wid, w) }.padding(vertical = 14.dp), style = TextStyle(fontSize = 17.sp, color = fg))
                }
            }
        }
    }
    // ios: Widgets/CharWidgets.swift:37,38
    private fun pick(wid: Int, w: WhoEnum) {
        lifecycleScope.launch {
            val id = GlanceAppWidgetManager(this@WhoIntent).getGlanceIdBy(wid)
            updateAppWidgetState(this@WhoIntent, id) { it[whoKey] = w.raw }
            Widgets.reloadAllTimelines(this@WhoIntent)
            Refresh.plan(this@WhoIntent)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
            finish()
        }
    }
}

// ios: Widgets/CharWidgets.swift:41
class CharEntry(
    // ios: Widgets/CharWidgets.swift:42
    var date: Instant = Instant.now(),
    // ios: Widgets/CharWidgets.swift:43
    val r: WReward,
    // ios: Widgets/CharWidgets.swift:44
    val c: WChar,
    // ios: Widgets/CharWidgets.swift:45
    var locked: Boolean,            // 아직 못 만난 인물 (실루엣). 7일 무료 뒤 잠금은 이것과 따로 Gate 가 한다
    // ios: Widgets/CharWidgets.swift:46
    var preview: Boolean = false,   // 위젯 갤러리 미리보기면 7일 무료 뒤에도 잠그지 않는다 (앱 미리보기도)
    // ios: Widgets/CharWidgets.swift:47
    var at: Instant? = null,        // 지금 값의 시세를 받은 시각과 출처 (큰 위젯 아래 줄 '3분 전 · 토스')
    // ios: Widgets/CharWidgets.swift:48
    var feed: String? = null,
    // ios: Widgets/CharWidgets.swift:49
    var frame: Int = 0,             // ↻ 받는 동안 몇 번째 그림인지 (전광판 인물이 포즈를 돌린다)
)

// ios: Widgets/CharWidgets.swift:52
object CharProvider {
    // ios: Widgets/CharWidgets.swift:53
    fun placeholder(): CharEntry = CharEntry(date = Instant.now(), r = WReward.sample, c = WChar.all[0], locked = false)
    // 정기 갱신·↻ 받는 중 포즈 돌리기·다음 대사(WidgetLine.bump)는 Provider.kt 의 Refresh 와 Intent.kt 의 ReloadWork 가 같이 한다
    // ios: Widgets/CharWidgets.swift:54,55,57,59,63,66,68,69
    fun timeline(who: WhoEnum): CharEntry = entry(who, preview = false).also { it.frame = if (Store.isReloading) Store.defaults.getInt("charFrame", 0) else 0 }
    // 위젯 갤러리 추천(recommendations)은 안드로이드에 없다: 위젯을 놓을 때 인물 고르기 화면이 뜬다
    // ios: Widgets/CharWidgets.swift:74
    fun entry(who: WhoEnum, preview: Boolean): CharEntry {
        // ios: Widgets/CharWidgets.swift:75
        val r = Store.read(WReward.serializer(), "reward.json") ?: WReward.sample
        // ↻·정기 갱신으로 위젯이 앱보다 나중에 받은 시세가 있으면 금액·오늘 등락·점선 끝점을 지금 값으로 (기본 위젯과 같은 값)
        // ios: Widgets/CharWidgets.swift:77
        if (!preview) Engine.liveHoldings()?.let { h ->
            // ios: Widgets/CharWidgets.swift:78
            r.total = h.total; r.dayChg = h.dayChg
            // ios: Widgets/CharWidgets.swift:79
            r.spark?.takeIf { it.isNotEmpty() }?.let { sp -> r.spark = sp.dropLast(1) + h.total / 1e4 }
        }
        // '홈 인물 따라가기'면 앱 홈에 둔 인물 (앱에서 홈 인물을 바꾸면 위젯도 바뀐다)
        // 못 만난 친구는 실루엣으로도 보이지 않는다 (2026-10-16 결정: 미리 보기 뺌). 고른 인물을 아직 못 만났으면 홈 인물로
        // ios: Widgets/CharWidgets.swift:83
        val met = { id: String -> id == "seri" || r.friendsOn.contains(id) }
        // ios: Widgets/CharWidgets.swift:84
        val want = if (who.follows) (r.homeFriend ?: "seri") else who.charId
        // ios: Widgets/CharWidgets.swift:85
        val c = WChar.of(if (preview || met(want)) want else (r.homeFriend?.let { if (met(it)) it else null } ?: "seri"))
        // ios: Widgets/CharWidgets.swift:86
        val e = CharEntry(date = Instant.now(), r = r, c = c, locked = false, preview = preview)
        // ios: Widgets/CharWidgets.swift:87
        if (!preview) { val s = Engine.snapshot(); if (!s.placeholder) { e.at = s.pricedAt; e.feed = s.feed } }
        // ios: Widgets/CharWidgets.swift:88
        return e
    }
}

/** 인물 위젯은 모두 작은 크기 (2026-10-17 큰·중간 삭제, 시안 B 4개 추가) */
// ios: Widgets/CharWidgets.swift:93,94,95,97,98
open class CharWidget(kind: String, val view: Pen.(CharEntry) -> Unit) : FaceWidget(kind) {
    override fun face(ctx: Context, state: Preferences): Face {
        val e = CharProvider.timeline(WhoEnum.of(state[whoKey]))
        val k = kind
        val g = Gate(kind = k, preview = e.preview, content = { this.kind = k; now = e.date; view(e) }, locked = { this.kind = k; now = e.date; charFrame(e, k) {} })
        val speech = if (g.open) charSpeech(k, e) else lockedSpeech(k)
        return Face(url = charURL(e, k), speech = speech) { charFrame(e, k) { view(e) } }
    }
}
// ios: Widgets/CharWidgets.swift:100
class CharHill : CharWidget("char.hill", { hillChar(it) })
// ios: Widgets/CharWidgets.swift:101
class CharLadder : CharWidget("char.ladder", { ladderChar(it) })
// ios: Widgets/CharWidgets.swift:102
class CharTower : CharWidget("char.tower", { towerChar(it) })
// ios: Widgets/CharWidgets.swift:103
class CharBoard : CharWidget("char.board", { boardChar(it) })
// ios: Widgets/CharWidgets.swift:104
class CharHeight : CharWidget("char.height", { heightChar(it) })
// ios: Widgets/CharWidgets.swift:105
class CharPuzzle : CharWidget("char.puzzle", { puzzleChar(it) })
// ios: Widgets/CharWidgets.swift:106
class CharGarden : CharWidget("char.garden", { gardenChar(it) })
// ios: Widgets/CharWidgets.swift:107
class CharTug : CharWidget("char.tug", { tugChar(it) })

class CharHillReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharHill() }
class CharLadderReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharLadder() }
class CharTowerReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharTower() }
class CharBoardReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharBoard() }
class CharHeightReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharHeight() }
class CharPuzzleReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharPuzzle() }
class CharGardenReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharGarden() }
class CharTugReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CharTug() }
