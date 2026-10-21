package com.naeilo.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.naeilo.app.core.Day
import com.naeilo.app.core.Fmt
import com.naeilo.app.core.Snapshot
import com.naeilo.app.core.Store
import com.naeilo.app.core.WL
import com.naeilo.app.core.WSmall
import com.naeilo.app.core.cformat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// MARK: 위젯 정의 (이름표는 Catalog, 잠금은 Gate, 값은 Provider. 새 위젯은 Catalog 에 한 줄, 여기 한 줄, Widgets.all 에 한 줄, 매니페스트·res/xml 에 한 벌)
// ios: Widgets/HomeWidgets.swift:5
class AssetSmall : HomeWidget("asset.small", { assetSmall(it) })
// ios: Widgets/HomeWidgets.swift:6
class PathSmall : HomeWidget("path.small", { pathSmall(it) })
// ios: Widgets/HomeWidgets.swift:7
class FutureSmall : HomeWidget("future.small", { futureSmall(it) })
// ios: Widgets/HomeWidgets.swift:8
class BlockSmall : HomeWidget("block.small", { blockSmall(it) })
// ios: Widgets/HomeWidgets.swift:9
class PaceSmall : HomeWidget("pace.small", { paceSmall(it) })
// ios: Widgets/HomeWidgets.swift:10
class TargetSmall : HomeWidget("target.small", { targetSmall(it) })
// ios: Widgets/HomeWidgets.swift:11
class MovesSmall : HomeWidget("moves.small", { movesSmall(it) })

class AssetSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AssetSmall() }
class PathSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PathSmall() }
class FutureSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FutureSmall() }
class BlockSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BlockSmall() }
class PaceSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PaceSmall() }
class TargetSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TargetSmall() }
class MovesSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MovesSmall() }

/** 기본 위젯 하나: 앱 색 바탕, 누르면 앱 첫 탭 */
// ios: Widgets/HomeWidgets.swift:14,15
open class HomeWidget(kind: String, val view: Pen.(Snapshot) -> Unit) : FaceWidget(kind) {
    // ios: Widgets/HomeWidgets.swift:16
    override fun face(ctx: Context, state: Preferences): Face {
        val e = Provider.getSnapshot()
        val g = Gate(kind = kind, preview = e.preview, content = {
            this.kind = this@HomeWidget.kind; isSample = e.snap.placeholder; now = e.date
            widgetBG(); view(e.snap)
        }, locked = { this.kind = this@HomeWidget.kind; lockedWidget(this@HomeWidget.kind) })
        val speech = if (g.open) charSpeak(Speech.of(kind, e.snap), kind = kind, money = kind == "asset.small" || kind == "future.small").label else lockedSpeech(kind)
        // 잠겼으면 구독·미션 화면으로 (Gate 가 정한 주소). 크기는 작은 위젯 하나 (.supportedFamilies: res/xml/w_*.xml 의 2×2)
        // ios: Widgets/HomeWidgets.swift:18
        val url = if (g.open) Catalog.openURL else if (Store.widgetsOpen) Catalog.openURL else Catalog.subscribeURL
        return Face(url = url, speech = speech) { g.body(this) }
    }
}

/** 말읽기(TalkBack)가 위젯마다 읽는 한 문장 (숫자 조각을 따로 읽지 않게). 금액을 숨겼으면 '숨김'으로 읽는다 */
// ios: Widgets/HomeWidgets.swift:42
object Speech {
    // ios: Widgets/HomeWidgets.swift:43
    fun of(kind: String, s: Snapshot, forced: Boolean = false): String {
        // ios: Widgets/HomeWidgets.swift:44
        val hide = forced || Store.isHidden(kind); val name = Catalog.name(kind)
        // ios: Widgets/HomeWidgets.swift:45
        val money: (Double) -> String = { if (hide) WL.t("숨김") else Fmt.eok(it) }
        // ios: Widgets/HomeWidgets.swift:46
        val pct: (Double?) -> String = { p -> p?.let { "${(it * 100).roundToInt()}%" } ?: "-" }
        // ios: Widgets/HomeWidgets.swift:47
        val mag: (Double) -> String = { cformat("%.1f%%", abs(it) * 100) }   // 부호 없이, 방향은 상승·하락으로
        // ios: Widgets/HomeWidgets.swift:48
        val chg = mapOf("p" to mag(s.dayChg), "dir" to WL.dir(s.dayChg))
        // ios: Widgets/HomeWidgets.swift:49
        var line: String
        // ios: Widgets/HomeWidgets.swift:50
        when (kind) {
            // ios: Widgets/HomeWidgets.swift:51,52
            "asset.small", "lock.asset" -> line = WL.t("총자산 {v}, 오늘 {p} {dir}", chg + ("v" to money(s.total)))
            // ios: Widgets/HomeWidgets.swift:53,54,55
            "future.small" -> line = if (s.term == null) WL.t("앱을 열면 계산합니다")
                else WL.t("{term} 예상 {v}, 지금보다 {p}", mapOf("term" to s.termLabel, "v" to (s.term?.let { money(it.p50) } ?: "-"), "p" to (s.term?.let { Fmt.pct(if (s.total > 0) it.p50 / s.total - 1 else 0.0, 0) } ?: "-")))
            // ios: Widgets/HomeWidgets.swift:56,57,58
            "lock.future" -> line = if (s.term == null && s.fan == null) WL.t("앱을 열면 계산합니다")
                else WL.t("{term} 예상 {v}, 목표 확률 {p}", mapOf("term" to s.termLabel, "v" to (s.term?.let { money(it.p50) } ?: "-"), "p" to pct(s.pGoal)))
            // ios: Widgets/HomeWidgets.swift:59
            "path.small" -> {
                // ios: Widgets/HomeWidgets.swift:60
                val d = Small.load() ?: (if (s.placeholder) WSmall.sample else null)
                if (d != null) {
                    // ios: Widgets/HomeWidgets.swift:61
                    val g = d.gapNow(s.total)
                    // ios: Widgets/HomeWidgets.swift:62
                    line = WL.t(if (g >= 0) "계획선보다 {p} 앞섬, 달성 확률 {q}" else "계획선보다 {p} 뒤처짐, 달성 확률 {q}", mapOf("p" to mag(g), "q" to pct(s.pGoal)))
                // ios: Widgets/HomeWidgets.swift:63
                } else { line = WL.t("앱을 열면 보여요") }
            }
            // ios: Widgets/HomeWidgets.swift:64
            "block.small" -> {
                // ios: Widgets/HomeWidgets.swift:65
                val d = s.cells - s.cellsPrev
                // ios: Widgets/HomeWidgets.swift:66
                line = WL.t("{b}번째 블록 100칸 중 {n}칸, 어제보다 {d}칸", mapOf("b" to "${min(s.cells / 100 + 1, 10)}", "n" to "${s.cells % 100}", "d" to (if (d >= 0) "+" else "") + "$d"))
            }
            // ios: Widgets/HomeWidgets.swift:67
            "pace.small" -> {
                // ios: Widgets/HomeWidgets.swift:68
                val d = Small.load() ?: (if (s.placeholder) WSmall.sample else null); val me = d?.me; val bk = d?.basket
                // ios: Widgets/HomeWidgets.swift:69
                line = if (me != null && bk != null) WL.t(if (me >= bk) "내가 {p} 빨라요" else "내가 {p} 느려요", mapOf("p" to mag(me - bk))) + ". " + WL.t("나 {a}, 모의 바구니 {b}", mapOf("a" to Fmt.pct(me), "b" to Fmt.pct(bk)))
                // ios: Widgets/HomeWidgets.swift:70
                else WL.t("앱에서 모의 바구니를 담으면 보여요")
            }
            // ios: Widgets/HomeWidgets.swift:71
            "target.small", "lock.target" -> {
                // ios: Widgets/HomeWidgets.swift:72
                val w = s.week
                // ios: Widgets/HomeWidgets.swift:73
                line = if (w != null) WL.t("지금 {v}, 예측 {m}, 예측보다 {p}, {hit}", mapOf("v" to money(w.act), "m" to money(w.p50), "p" to Fmt.pct(w.act / w.p50 - 1), "hit" to s.hitText))
                // ios: Widgets/HomeWidgets.swift:74
                else WL.t("이번 주 예측 대기")
            }
            // ios: Widgets/HomeWidgets.swift:75
            "moves.small" -> {
                // ios: Widgets/HomeWidgets.swift:76
                line = WL.t("총자산 {v}, 오늘 {p} {dir}", chg + ("v" to money(s.total)))
                // ios: Widgets/HomeWidgets.swift:77
                val ms = s.moves.take(4).map { "${it.t} ${mag(it.pct)} ${WL.dir(it.pct)}" }
                // ios: Widgets/HomeWidgets.swift:78
                if (ms.isNotEmpty()) line += ". " + ms.joinToString(", ")
            }
            // ios: Widgets/HomeWidgets.swift:79,80
            "lock.goal" -> line = WL.t("목표 {p} 진행", mapOf("p" to pct(s.progress)))
            // ios: Widgets/HomeWidgets.swift:81,82
            else -> line = ""
        }
        // ios: Widgets/HomeWidgets.swift:84
        if (s.placeholder) line += ". " + WL.t("예시 값")
        // ios: Widgets/HomeWidgets.swift:85
        return if (line.isEmpty()) name else "$name. $line"
    }
}
