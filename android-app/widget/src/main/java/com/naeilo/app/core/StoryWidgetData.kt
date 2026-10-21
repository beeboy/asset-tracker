package com.naeilo.app.core

import android.net.Uri
import kotlinx.serialization.Serializable

/** 서재 위젯이 읽는 값 (앱이 story.json 으로 쓴다). 위젯은 스크롤이 안 돼서 읽던 자리의 한 문단만 보여 주고, 누르면 앱의 그 장으로 간다 */
// ios: WidgetShared/StoryWidgetData.swift:4
@Serializable
data class WStory(
    // ios: WidgetShared/StoryWidgetData.swift:5
    var open: Boolean,          // 서재가 열렸는지 (앱 시작 3단계)
    // ios: WidgetShared/StoryWidgetData.swift:6
    var chapter: Int,           // 0 프롤로그 … 5 5장
    // ios: WidgetShared/StoryWidgetData.swift:7
    var label: String,          // "2장 · 이름을 허락하는 사람" (고른 언어)
    // ios: WidgetShared/StoryWidgetData.swift:8
    var line: String,           // 읽던 자리의 문단 (마크다운 기호는 뺀 글)
    // ios: WidgetShared/StoryWidgetData.swift:9
    var friend: String,         // 그 장과 같이 열리는 인물 (위젯 그림)
    // ios: WidgetShared/StoryWidgetData.swift:10
    var progress: Double,       // 이 장에서 읽은 만큼 0~1
    // ios: WidgetShared/StoryWidgetData.swift:11
    var lang: String,           // ko | en
) {
    // ios: WidgetShared/StoryWidgetData.swift:16
    val url: Uri get() = Uri.parse(if (open) "naeilo://read/$chapter" else "naeilo://unlock")

    companion object {
        /** 그 장의 도트 그림 (앱이 png 로 둔다. 위젯 묶음에는 장 그림이 없다) */
        // ios: WidgetShared/StoryWidgetData.swift:14
        const val artFile = "story_art.png"

        // ios: WidgetShared/StoryWidgetData.swift:18,19,20
        val sample = WStory(open = true, chapter = 1, label = "1장 · 무명의 인터페이스",
            line = "그는 주식 앱을 닫고 대화형 인공지능 앱을 열었다. 질문을 길게 쓰는 편이 아니었다.",
            friend = "seri", progress = 0.3, lang = "ko")
    }
}
