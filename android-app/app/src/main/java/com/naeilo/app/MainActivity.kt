package com.naeilo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.naeilo.app.core.Store
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Sample
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.Market

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
        // 시세·추가한 종목 저장 파일과 번들 자료는 첫 화면을 그린 뒤 딴 줄에서 미리 읽는다
        // (앱 시작과 겹치면 시작이 느려지고, 안 읽어 두면 탭을 처음 열 때 파일을 기다린다)
        window.decorView.post { Thread { runCatching { Market.shared; CustomSymbols.shared; Sample.symbols } }.start() }
    }

    override fun onStart() { super.onStart(); model.onForeground(true) }
    override fun onStop() { super.onStop(); model.onForeground(false) }
}
