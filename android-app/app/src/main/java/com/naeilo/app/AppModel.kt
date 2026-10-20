package com.naeilo.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 앱 상태 뼈대: 지금은 탭·첫 실행·화면 모드만. 나머지(보유·루트·미션…)는 모델 이식 단계에서 붙인다.
 * 저장은 iOS UserDefaults 자리인 SharedPreferences (키 이름 같게).
 */
class AppModel(ctx: Context, intent: Intent?) {
    private val prefs = ctx.getSharedPreferences("naeilo", Context.MODE_PRIVATE)
    private val cr = ctx.contentResolver

    var tab by mutableStateOf(launchTab(intent))   // ios: Data/AppModel.swift:339
    // ios: Data/AppModel.swift:341 캡처용으로 시작 탭을 넘기면 첫 질문은 건너뛴다
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false) || intent?.getStringExtra("tab") != null)
    var appearance by mutableStateOf(prefs.getString("appearance", null) ?: "system")   // ios: Data/AppModel.swift:343 system · light · dark

    /** scenePhase == .active: 앱이 앞에 있는지 (onStart/onStop) */
    var active by mutableStateOf(false)   // ios: App.swift:27
    /** 동작 줄이기: 앱을 앞으로 가져올 때마다 다시 읽는다 */
    var reduceMotion by mutableStateOf(false)

    fun onForeground(on: Boolean) {
        active = on
        if (on) reduceMotion = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    fun finishStart() {   // ios: Data/AppModel.swift:805
        onboarded = true; prefs.edit().putBoolean("onboarded", true).apply()
    }

    companion object {
        // 에뮬레이터 캡처용: adb shell am start -n com.naeilo.app/.MainActivity --es tab hold 처럼 시작 탭을 고를 수 있다
        // ios: App.swift:130,131
        fun launchTab(intent: Intent?): Tab = when (intent?.getStringExtra("tab")) {
            "hold" -> Tab.hold            // ios: App.swift:132
            "analysis" -> Tab.analysis    // ios: App.swift:133
            "board" -> Tab.board          // ios: App.swift:134
            "settings" -> Tab.settings    // ios: App.swift:135
            else -> Tab.home              // ios: App.swift:136
        }
    }
}
