package com.naeilo.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.text.Text

// 뼈대: 위젯 자리. 내용·등록은 위젯 단계에서 (ios-app/Widgets/)
class NaeiloWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Text("naeilo") }
    }
}

class NaeiloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NaeiloWidget()
}
