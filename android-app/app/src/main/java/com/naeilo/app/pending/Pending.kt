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


// TODO(Support): 진짜 이식 때 지움 (구독 단계)
enum class SubPlan { monthly, yearly; val nameText: String get() = if (this == monthly) "월 구독" else "연 구독" }
class Support {
    val dayN: Int = 1; val testBuild: Boolean = false; val jws: String? = null
    val dev: Boolean = false   // TODO(Support): 개발자 빌드
    val plan: SubPlan? = null; val inTrial: Boolean get() = dayN <= 7; val trialLeft: Int get() = maxOf(0, 7 - dayN + 1)
    val pro: Boolean get() = plan != null || inTrial
    fun start() {}; fun tick() {}
    companion object { val shared = Support() }
}
// TODO(WidgetBridge): 진짜 이식 때 지움
object WidgetBridge { fun write(m: AppModel) {} }
// TODO(SurfCloset): 진짜 이식 때 지움. 저장 키는 iOS 와 같다 (백업 파일에 들어간다)
object SurfCloset { val keys = listOf("surf.total", "surf.fever", "surf.splash", "surf.snap", "surf.hard", "surf.board", "surf.hat"); fun keep() {} }



// TODO(SupportView 구독 단계)
@Composable fun SupportView() {}



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
// TODO(TownView 동네 I): 소식판의 [동네] 버튼과 전체 화면
@Composable fun TownButton() {}
// TODO(SupportView 구독): 구독하면 열리는 화면
@Composable fun SubLocked(what: String) {}





// G2b(외부 요인·금리 시나리오) 화면이 부르는데 아직 옮기지 않은 이름의 자리. 진짜 이식이 오면 이 파일의 해당 줄을 지운다.

// TODO(Data/Notifier.swift EventRetro): 일정 전후 평가액 기록·결과 알림
object EventRetro {
    /** 일정 하나의 미리 본 범위(amt)와 일정 전 평가액(base), 다음에 연 때 평가액(after) */
    class Snap(val kind: String, val day: String, val amt: Double, val base: Double, val after: Double?)
    /** record 에 넘기는 일정 한 줄 (Swift (id:kind:day:amt:) 튜플 자리) */
    class Rec(val id: String, val kind: String, val day: String, val amt: Double)
    fun record(evs: List<Rec>, total: Double) {}
    val latest: Snap? get() = null
    fun isAsked(id: String, day: String): Boolean = false
    suspend fun ask(id: String, kind: String, day: String, on: Boolean) {}
}


// TODO(Data/Notifier.swift Notifier.request): 알림 허락 결과 (Swift 는 Bool 을 돌려준다. Pending.kt 의 Notifier.request 는 아직 Unit)
suspend fun notifierRequest(): Boolean { Notifier.request(); return false }
