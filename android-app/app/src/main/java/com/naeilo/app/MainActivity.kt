package com.naeilo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// ios: App.swift:4 (@main → 런처 액티비티)
class MainActivity : ComponentActivity() {
    private val model by lazy { AppModel(applicationContext, intent) }   // ios: App.swift:6

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
