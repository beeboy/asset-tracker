package com.naeilo.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * 앱 언어가 바뀌었을 때 앱을 새로 띄우는 잠깐 화면 (따로 된 프로세스 ":restart").
 * 아이폰은 설정에서 언어를 바꾸면 앱이 새로 시작된다. 안드로이드도 같게: 본 프로세스가 끝난 뒤 첫 화면을 다시 연다
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Process.killProcess(Process.myPid())
    }
}
