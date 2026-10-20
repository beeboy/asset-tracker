package com.naeilo.app.pending

import com.naeilo.app.AppModel
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.naeilo.app.core.WReward
import com.naeilo.app.data.Friend
import com.naeilo.app.data.MeChar
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.Theme

// 아직 옮기지 않은 단계(B·C·D 밖) 이름의 자리. 컴파일될 만큼만 빈 값·기본값을 둔다.
// 진짜 이식이 오면 그 단계에서 이 파일의 해당 줄을 지운다. Route·GoalWeek 는 저장(Progress) JSON 에 들어가므로 경우 이름은 iOS 와 같게 둔다.

// TODO(AnalysisView): 진짜 이식 때 지움
enum class AnalysisRoute { forecast, myPath, rateScen, external, dividend, fx, glance, calm }

// TODO(Mission3ResultView): 진짜 이식 때 지움
object Mission3ResultView { fun pp(d: Double): String = "" }
// TODO(Support): 진짜 이식 때 지움 (구독 단계)
enum class SubPlan { monthly, yearly; val nameText: String get() = if (this == monthly) "월 구독" else "연 구독" }
class Support {
    val dayN: Int = 1; val testBuild: Boolean = false; val jws: String? = null
    val plan: SubPlan? = null; val inTrial: Boolean get() = dayN <= 7; val trialLeft: Int get() = maxOf(0, 7 - dayN + 1)
    fun start() {}; fun tick() {}
    companion object { val shared = Support() }
}
// TODO(Story): 진짜 이식 때 지움
class Story { enum class Kind { side }; fun isDone(k: Kind, i: Int): Boolean = false; companion object { val shared = Story() } }
// TODO(Lines): 진짜 이식 때 지움
object Lines { fun pick(key: String, vars: Map<String, String> = emptyMap()): String? = null }
// TODO(Remind): 진짜 이식 때 지움 (홈 리마인드 Data/Remind.swift·Views/RemindViews.swift: 단계 미정)
enum class RemindCat(val label: String) { done("달성"), doing("진행 중"), event("일정"), market("증시 요약"), analysis("분석"), news("설정·새 소식") }
// TODO(Remind)
val RemindCat.color: Color @Composable get() = when (this) { RemindCat.done -> Theme.green; RemindCat.doing -> Theme.purple; RemindCat.event -> Theme.orange; RemindCat.market, RemindCat.analysis -> Theme.blue; RemindCat.news -> Theme.teal }
// TODO(Remind)
sealed class RemindGo { object nowhere : RemindGo() }
// TODO(Remind)
class RemindItem(val id: String, val cat: RemindCat, val text: String, val go: RemindGo = RemindGo.nowhere, val day: String? = null, val sub: String? = null)
// TODO(Remind)
class Remind {
    class Week(val lo: Double, val hi: Double, val pos: String?)
    class Log(val weeks: Map<String, Week>, val guessHits: Int?)
    val log = Log(emptyMap(), null)
    val items: List<RemindItem> = emptyList(); val fresh: List<RemindItem> = emptyList(); val doing: List<RemindItem> = emptyList()
    val strip: RemindItem? = null; val bubble: RemindItem? = null
    fun isOn(c: RemindCat): Boolean = true
    fun nextBubble() {}; fun markAllSeen() {}; fun refresh(m: AppModel) {}
    companion object {
        val shared = Remind()
        fun week(at: Instant): String = ""
        fun endedWeeks(w: Map<String, Week>): List<String> = emptyList()
    }
}
// TODO(Story): 진짜 이식 때 지움. 원고 장은 받은 JSON 그대로 둔다 (StoreAPI.Book)
typealias StoryChapter = JsonElement
// TODO(Story): 진짜 이식 때 지움. 경우 이름 = iOS rawValue
enum class StoryLang { ko, en }
// TODO(Story): 진짜 이식 때 지움. 경우 이름 = iOS rawValue
enum class StoryBook { side, vol1 }
// TODO(LineClock): 진짜 이식 때 지움
class LineClock { fun next() {}; companion object { val shared = LineClock() } }
// TODO(WidgetBridge): 진짜 이식 때 지움
object WidgetBridge { fun write(m: AppModel) {} }
// TODO(SurfCloset): 진짜 이식 때 지움. 저장 키는 iOS 와 같다 (백업 파일에 들어간다)
object SurfCloset { val keys = listOf("surf.total", "surf.fever", "surf.splash", "surf.snap", "surf.hard", "surf.board", "surf.hat"); fun keep() {} }
// TODO(TownBook): 진짜 이식 때 지움
class TownBook { fun reload() {}; companion object { val shared = TownBook() } }
// TODO(TownLog): 진짜 이식 때 지움
class TownLog { fun reload() {}; companion object { val shared = TownLog() } }

// TODO(Remind): 소식을 누르면 그 화면으로 (Data/Remind.swift AppModel.go)
fun AppModel.go(g: RemindGo) {}
// TODO(Remind): "내일", "10/22 (D-11)"
object MarketEvents { fun `when`(day: String): String = day }
// TODO(RemindViews): 소식함 시트·리마인드 설정
@Composable fun RemindSheet() {}
// TODO(RemindViews)
@Composable fun RemindOptions() {}

// TODO(MeViews 인물 단계): 기본 인물은 spr_/sil_ 그림 (진짜와 같음), 내 캐릭터는 아직 빈 칸
@Composable fun CharPixel(id: String, width: Dp, height: Dp, modifier: Modifier = Modifier, sil: Boolean = false) {
    if (id == "me") Box(modifier.size(width, height)) else PixelN((if (sil) "sil_" else "spr_") + id, width, height, modifier)
}
// TODO(MeViews 인물 단계)
@Composable fun MeAvatar(me: MeChar, width: Dp, height: Dp, modifier: Modifier = Modifier) { Box(modifier.size(width, height)) }

// TODO(홈에서 여는 다른 단계 화면): 쉼터·평행세계·목표 정하기·서재·내 캐릭터·인물 상세
@Composable fun ShelterView(onlyLibrary: Boolean = false) {}
// TODO(ParallelView)
@Composable fun ParallelView() {}
// TODO(ParallelView)
@Composable fun ParallelBasketView() {}
// TODO(ParallelView): 홈 평행세계 카드
@Composable fun ParallelHomeCard() {}
// TODO(GoalViews)
@Composable fun GoalSetView(fromSettings: Boolean = false) {}
// TODO(Reader)
@Composable fun ReaderView(index: Int, book: StoryBook = StoryBook.side) {}
// TODO(MeViews)
@Composable fun MeEditView() {}
// TODO(CharacterDetail)
@Composable fun CharacterDetailView(friend: Friend, invest: Boolean, focus: Int?) {}
// TODO(MissionViews): 회복 루트 매달 더 넣기 카드
@Composable fun DepositLeverCard(plan: Any?) {}
// TODO(SupportView 구독 단계)
@Composable fun SupportView() {}

// TODO(AnalysisDetail): 내 길 (홈 MiniPath 가 읽는다)
class MyPath(val start: Double, val end: Double, val key: Double, val monthly: Double, val pts: List<Pair<Double, Double>>, val act: (Double) -> Double, val need: (Double) -> Double)
// TODO(AnalysisDetail)
val AppModel.myPath: MyPath get() = MyPath(1.0 / 365.25, 1.0, gK * 1e4, 0.0, emptyList(), { total }, { total })
// TODO(AnalysisDetail)
val AppModel.myPathGapToday: Double get() { val p = myPath; val n = p.need(p.start); return if (n > 0) p.act(p.start) / n - 1 else 0.0 }


// TODO(Notifier 알림 단계)
object Notifier {
    enum class Status { allowed, denied, unknown }
    suspend fun status(): Status = Status.unknown
    suspend fun request() {}
    suspend fun test(m: AppModel) {}
    suspend fun reschedule(m: AppModel) {}
    suspend fun check(m: AppModel) {}
    fun morningLine(m: AppModel): String = ""
}

// TODO(Catalog 위젯 단계): 기본 위젯 이름표 (Widgets/Views/Catalog.swift 와 같은 값)
object Catalog {
    class Item(val kind: String, val name: String, val desc: String, val size: String)
    val base: List<Item> = listOf(
        Item("asset.small", "자산 추이", "총자산·오늘 등락·최근 10일 흐름", "작은"),
        Item("path.small", "내 길", "계획선보다 앞섰는지와 달성 확률", "작은"),
        Item("future.small", "목표일 전망", "목표일 예상과 지금보다 몇 %", "작은"),
        Item("block.small", "지금 채우는 블록", "목표를 1000칸으로 나눠 지금 채우는 100칸", "작은"),
        Item("pace.small", "목표 페이스", "나와 모의 바구니, 누가 빠른지", "작은"),
        Item("target.small", "이번 주 과녁", "금요일 마감 예측과 지금 위치", "작은"),
        Item("moves.small", "오늘의 움직임", "비중 큰 종목의 오늘 등락 히트맵", "작은"),
        Item("lock.asset", "자산 추이", "총자산·오늘 등락·1달 흐름", "잠금 화면"),
        Item("lock.goal", "목표 진행", "목표 대비 %", "잠금 화면"),
        Item("lock.future", "3년 뒤", "3년 뒤 예상 범위", "잠금 화면"),
        Item("lock.target", "이번 주 과녁", "금요일 마감 예측과 지금", "잠금 화면"),
    )
}
// TODO(CharWidgets 위젯 단계): 인물 위젯 한 칸
class CharEntry(val r: WReward, val c: com.naeilo.app.core.WChar, val locked: Boolean, val preview: Boolean = false)
// TODO(CharWidgets)
@Composable fun CharBG(c: com.naeilo.app.core.WChar, locked: Boolean, dark: Boolean) { Box(Modifier) }
// TODO(CharWidgets): 인물 위젯 8가지
@Composable fun HillCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun LadderCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun TowerCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun BoardCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun HeightCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun PuzzleCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun GardenCharView(e: CharEntry) {}
// TODO(CharWidgets)
@Composable fun TugCharView(e: CharEntry) {}
// TODO(BrickGameView 게임 단계): 미니 게임 중엔 1분 시세 갱신을 미룬다
object GamePause { @Volatile var on: Boolean = false }
// TODO(WidgetBridge 위젯 단계): 위젯 다시 그리기
object WidgetCenter { fun reloadAllTimelines() {} }
// TODO(CharWidgets 위젯 단계): 인물 위젯 다크 모양·금액 가리기 (charDark·charHide 환경값)
val LocalCharDark = androidx.compose.runtime.compositionLocalOf { false }
// TODO(CharWidgets)
val LocalCharHide = androidx.compose.runtime.compositionLocalOf { false }
// TODO(HoldingDetailView 종목 상세 F2)
@Composable fun HoldingDetailView(sym: com.naeilo.app.data.Symbol, period: com.naeilo.app.data.Period) {}
// TODO(AddHoldingView 종목 추가 F2)
@Composable fun AddHoldingView() {}
// TODO(TownView 동네 I): 소식판의 [동네] 버튼과 전체 화면
@Composable fun TownButton() {}
