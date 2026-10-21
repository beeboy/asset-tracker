package com.naeilo.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 위젯 고르기 화면 미리보기 (안드로이드 15+): 앱을 새로 깔았을 때 한 번만, 지금 값으로 그린 그림을 올린다
// 안드로이드가 올리는 횟수를 제한하므로 설치(업데이트) 시각이 바뀔 때만 다시 올린다
object Previews {
    fun publish(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val prefs = app.getSharedPreferences("w_preview", Context.MODE_PRIVATE)
                val stamp = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
                if (prefs.getLong("stamp", 0L) == stamp) return@launch
                Widgets.reloadAllTimelines(app)   // 새로 깐 뒤 홈에 둔 위젯도 새 그림으로
                if (Build.VERSION.SDK_INT < 35) { prefs.edit().putLong("stamp", stamp).apply(); return@launch }
                val mgr = AppWidgetManager.getInstance(app)
                for (w in Widgets.all) {
                    val cls = Widgets.receiverOf[w.javaClass] ?: continue
                    val f = w.face(app, emptyPreferences())
                    val r = render(app, guideW(app), guideW(app) * tileRatio, isDark(app), designW = guideW(app), draw = f.draw)
                    val rv = RemoteViews(app.packageName, R.layout.w_preview).apply { setImageViewBitmap(R.id.w_preview_img, r.bitmap) }
                    mgr.setWidgetPreview(ComponentName(app, cls), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, rv)
                }
                prefs.edit().putLong("stamp", stamp).apply()
            }
        }
    }
}
