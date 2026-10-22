package com.naeilo.app

import com.naeilo.app.data.persists
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
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
        // 앱 언어가 바뀌면(설정 > 앱 > 언어) 아이폰처럼 앱을 새로 띄운다: 언어는 시작할 때 한 번만 읽는다 (AppModel.en)
        if (relaunchIfLanguageChanged()) return
        enableEdgeToEdge()
        // 첫 화면에서 뒤로 가기: 앱을 끝내지 않고 뒤로 보낸다 (아이폰처럼 다시 열면 보던 화면 그대로, 하얀 시작 화면 없이).
        // 런처 표시가 별칭(.IconDefault)에 있어 시스템이 이 액티비티를 끝내 버린다. 화면 안 뒤로 가기(NavStack·Sheet)가 나중에 걸려 먼저 받는다
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        // ios: App.swift:8 알림 허락 창을 띄울 화면·알림 채널 (누름 처리는 App.kt openIntent → openNotification)
        model.let { Notifier.attach(this) }
        // ios: App.swift:12,13
        setContent { NaeiloApp(model, window) }
        openIntent(model, intent)   // 앱을 처음 여는 주소·알림
        com.naeilo.widget.Previews.publish(this)   // 새로 깐 뒤 홈 위젯 다시 그리기
        if (intent?.getBooleanExtra("exportPreviews", false) == true) com.naeilo.widget.Previews.export(this)   // 위젯 고르기 그림 뽑기 (adb 전용)
        // 시세·추가한 종목 저장 파일과 번들 자료는 첫 화면을 그린 뒤 딴 줄에서 미리 읽는다
        // (앱 시작과 겹치면 시작이 느려지고, 안 읽어 두면 탭을 처음 열 때 파일을 기다린다)
        window.decorView.post { Thread { runCatching { Market.shared; CustomSymbols.shared; Sample.symbols } }.start() }
        // ios: App.swift:10 구독: 지금 구독 확인, 거래 듣기 (Play 결제 연결도 첫 화면 뒤로 미룬다)
        window.decorView.post { Support.shared.start() }
    }

    /** 지금 화면 언어와 시작할 때 읽은 언어가 다르면 프로세스를 새로 시작한다 */
    private fun relaunchIfLanguageChanged(): Boolean {
        Store.init(applicationContext); Defaults.init(applicationContext)   // AppModel.en 이 저장소에 언어를 적는다
        val now = java.util.Locale.getDefault().language == "en"   // AppModel.en 과 같은 곳에서 읽는다 (다르게 읽으면 다시 띄우기가 멈추지 않을 수 있다)
        if (now == AppModel.en) return false
        startActivity(Intent(this, RestartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
        return true
    }

    /** 이미 떠 있는 앱으로 온 주소·알림 (singleTop) */
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openIntent(model, intent) }

    override fun onStart() { super.onStart(); model.onForeground(true) }
    override fun onStop() {
        super.onStop(); model.onForeground(false)
        if (model.persists) com.naeilo.app.net.Sync.shared.flush(model)   // ios: App.swift:61 동기화 올리기는 앱을 닫을 때 한 번
    }
}
