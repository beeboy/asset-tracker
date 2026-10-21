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


// TODO(WidgetBridge): 진짜 이식 때 지움
object WidgetBridge { fun write(m: AppModel) {} }



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
// TODO(WidgetBridge 위젯 단계): 위젯 다시 그리기
object WidgetCenter { fun reloadAllTimelines() {} }
// TODO(CharWidgets 위젯 단계): 인물 위젯 다크 모양·금액 가리기 (charDark·charHide 환경값)
val LocalCharDark = androidx.compose.runtime.compositionLocalOf { false }
// TODO(CharWidgets)
val LocalCharHide = androidx.compose.runtime.compositionLocalOf { false }
