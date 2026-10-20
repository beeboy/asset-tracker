package com.naeilo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults

// ios: App.swift:4 (@main → 런처 액티비티)
class MainActivity : ComponentActivity() {
    // 저장소(Store·Defaults)를 먼저 한 번 열고 모델을 만든다 (모델이 만들 때 저장값을 읽는다)
    private val model by lazy { Store.init(applicationContext); Defaults.init(applicationContext); AppModel(applicationContext, intent) }   // ios: App.swift:6

    // ios: App.swift:7
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ios: App.swift:12,13
        setContent { NaeiloApp(model, window) }
    }

    override fun onStart() { super.onStart(); model.onForeground(true) }
    override fun onStop() { super.onStop(); model.onForeground(false) }
}
