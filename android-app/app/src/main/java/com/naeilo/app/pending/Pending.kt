package com.naeilo.app.pending

import com.naeilo.app.AppModel
import kotlinx.serialization.json.JsonElement
import java.time.Instant

// 아직 옮기지 않은 단계(B·C·D 밖) 이름의 자리. 컴파일될 만큼만 빈 값·기본값을 둔다.
// 진짜 이식이 오면 그 단계에서 이 파일의 해당 줄을 지운다. Route·GoalWeek 는 저장(Progress) JSON 에 들어가므로 경우 이름은 iOS 와 같게 둔다.

// TODO(SettingsView): 진짜 이식 때 지움
enum class SettingsRoute { alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence }
// TODO(AnalysisView): 진짜 이식 때 지움
enum class AnalysisRoute { forecast, myPath, rateScen, external, dividend, fx, glance, calm }

// TODO(Mission3ResultView): 진짜 이식 때 지움
object Mission3ResultView { fun pp(d: Double): String = "" }
// TODO(Support): 진짜 이식 때 지움
class Support { val dayN: Int = 1; val testBuild: Boolean = false; val jws: String? = null; companion object { val shared = Support() } }
// TODO(Story): 진짜 이식 때 지움
class Story { enum class Kind { side }; fun isDone(k: Kind, i: Int): Boolean = false; companion object { val shared = Story() } }
// TODO(Lines): 진짜 이식 때 지움
object Lines { fun pick(key: String, vars: Map<String, String> = emptyMap()): String? = null }
// TODO(Remind): 진짜 이식 때 지움
class Remind {
    class Week(val lo: Double, val hi: Double, val pos: String?)
    class Log(val weeks: Map<String, Week>, val guessHits: Int?)
    val log = Log(emptyMap(), null)
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
