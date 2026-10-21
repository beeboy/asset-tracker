package com.naeilo.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.naeilo.app.core.Store
import com.naeilo.app.core.WStory
import java.time.Instant

// 서재 위젯: 외전 『이종 공명』에서 읽던 자리의 한 문단. 위젯은 스크롤이 안 되니 한 문단만 보여 주고, 누르면 앱의 그 장 그 자리로 간다.
// 값은 앱이 story.json 으로 쓴다 (언어도 앱의 읽기 화면에서 고른 것). 서재가 아직 안 열렸으면 앱 시작 3단계로 간다.
// ↻: 시세는 받지 않고 서재 위젯만 다시 그린다 (StoryReloadIntent). 그동안 글·막대가 흐려지고 인물이 뛰어오른 모습으로 그린다

// ios: Widgets/StoryWidget.swift:8
class StoryEntry(
    // ios: Widgets/StoryWidget.swift:9
    val date: Instant,
    // ios: Widgets/StoryWidget.swift:10
    val s: WStory,
    // ios: Widgets/StoryWidget.swift:11
    val preview: Boolean = false,   // 위젯 갤러리 미리보기면 잠그지 않는다 (다른 위젯과 같이)
)

// ios: Widgets/StoryWidget.swift:14
object StoryProvider {
    // ios: Widgets/StoryWidget.swift:15
    fun placeholder(): StoryEntry = StoryEntry(date = Instant.now(), s = WStory.sample)
    // 읽던 자리가 바뀔 때 앱이 다시 그리게 한다 (WidgetBridge). 7일 무료가 끝나는 때 다시 그리기는 Refresh 가 trialEnd 까지 예약한다
    /** 위젯 갤러리·앱 미리보기: 서재가 아직 안 열렸으면 예시 */
    // ios: Widgets/StoryWidget.swift:16,17,18
    fun getSnapshot(preview: Boolean): StoryEntry { val s = Store.read(WStory.serializer(), "story.json")
        return StoryEntry(date = Instant.now(), s = if (preview && !(s?.open ?: false)) WStory.sample else s ?: WStory.sample, preview = preview) }
    // ios: Widgets/StoryWidget.swift:20,24,25
    fun getTimeline(): StoryEntry {
        // ios: Widgets/StoryWidget.swift:22
        val s = Store.read(WStory.serializer(), "story.json") ?: WStory(open = false, chapter = 0, label = "프롤로그 · 핵심 코어", line = "앱을 열면 서재가 준비돼요.", friend = "seri", progress = 0.0, lang = "ko")
        return StoryEntry(date = Instant.now(), s = s)
    }
}

// ios: Widgets/StoryWidget.swift:29,30,31,32,33
class StoryWidget : FaceWidget("story") {
    override fun face(ctx: Context, state: Preferences): Face {
        val e = if (Previews.sample) StoryEntry(java.time.Instant.now(), WStory.sample, preview = true) else StoryProvider.getTimeline()
        val g = Gate(kind = "story", preview = e.preview, content = { kind = "story"; now = e.date; storyWidget(e.s) }, locked = { kind = "story"; lockedWidget("story") })
        val url = if (g.open) storyURL(e.s) else if (Store.widgetsOpen) Catalog.openURL else Catalog.subscribeURL
        return Face(url = url, speech = if (g.open) storySpeech(e.s) else lockedSpeech("story")) { g.body(this) }
    }
}
class StoryWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StoryWidget() }
