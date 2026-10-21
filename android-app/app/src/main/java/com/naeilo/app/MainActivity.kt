package com.naeilo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.naeilo.app.data.Notifier
import com.naeilo.app.data.Support
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
        // ios: App.swift:8 알림 허락 창을 띄울 화면·알림 채널 (누름 처리는 App.kt openIntent → openNotification)
        model.let { Notifier.attach(this) }
        // ios: App.swift:12,13
        setContent { NaeiloApp(model, window) }
        openIntent(model, intent)   // 앱을 처음 여는 주소·알림
        // 시세·추가한 종목 저장 파일과 번들 자료는 첫 화면을 그린 뒤 딴 줄에서 미리 읽는다
        // (앱 시작과 겹치면 시작이 느려지고, 안 읽어 두면 탭을 처음 열 때 파일을 기다린다)
        window.decorView.post { Thread { runCatching { Market.shared; CustomSymbols.shared; Sample.symbols } }.start() }
        // ios: App.swift:10 구독: 지금 구독 확인, 거래 듣기 (Play 결제 연결도 첫 화면 뒤로 미룬다)
        window.decorView.post { Support.shared.start() }
    }

    /** 이미 떠 있는 앱으로 온 주소·알림 (singleTop) */
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openIntent(model, intent) }

    override fun onStart() { super.onStart(); model.onForeground(true) }
    override fun onStop() { super.onStop(); model.onForeground(false) }
}
