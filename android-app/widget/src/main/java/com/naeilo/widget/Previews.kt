package com.naeilo.widget

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// 위젯 고르기 미리보기: 삼성 위젯 고르기는 앱이 올린 그림(setWidgetPreview)을 안 받아서, 예시 값 그림을 res/drawable-nodpi 에 넣고 xml 의 previewImage 로 건다.
// 그림 뽑기: adb shell am start -n <앱>/com.naeilo.app.MainActivity --ez exportPreviews true → 앱 files/previews/w_*_preview.png
object Previews {
    /** true 인 동안 위젯이 내 값 대신 예시 값으로 그린다 (뽑기 전용) */
    @Volatile var sample = false

    /** 새로 깐 뒤 앱을 처음 열면 홈에 둔 위젯을 새 그림으로 다시 그린다 */
    fun publish(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val prefs = app.getSharedPreferences("w_preview", Context.MODE_PRIVATE)
                val stamp = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
                if (prefs.getLong("stamp", 0L) == stamp) return@launch
                Widgets.reloadAllTimelines(app)
                prefs.edit().putLong("stamp", stamp).apply()
            }
        }
    }

    /** 예시 값으로 위젯마다 그림 한 장 (안내 그림 크기, 홈 칸 비율) */
    fun export(ctx: Context) {
        val app = ctx.applicationContext
        val dir = File(app.getExternalFilesDir(null), "previews").apply { mkdirs() }
        sample = true
        try {
            for (w in Widgets.all) {
                val f = w.face(app, emptyPreferences())
                val gw = guideW(app)
                val r = render(app, gw, gw * tileRatio, isDark(app), designW = gw, draw = f.draw)
                File(dir, "w_" + w.kind.replace('.', '_') + "_preview.png").outputStream().use { r.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { sample = false }
    }
}
