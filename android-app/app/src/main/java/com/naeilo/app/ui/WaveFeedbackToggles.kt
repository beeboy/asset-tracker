package com.naeilo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Views/Wave/WaveFeedback.swift 중 설정 화면이 쓰는 스위치 두 줄 (소리·진동을 내는 쪽은 게임 단계에서 옮긴다)

/** 파도 타기 효과음·진동 켜기 (게임이 읽는 저장 이름) */
// ios: Views/Wave/WaveFeedback.swift:10
object WaveFeedbackKeys { const val soundKey = "waveSound"; const val hapticKey = "waveHaptic" }

/** 설정 '화면' 묶음에 넣는 스위치 두 줄 */
// ios: Views/Wave/WaveFeedback.swift:129,133
@Composable
fun WaveFeedbackToggles() {
    var sound by rememberPrefBool(WaveFeedbackKeys.soundKey, true)   // ios: Views/Wave/WaveFeedback.swift:130
    var haptic by rememberPrefBool(WaveFeedbackKeys.hapticKey, true)   // ios: Views/Wave/WaveFeedback.swift:131

    // ios: Views/Wave/WaveFeedback.swift:143,144,145,146,147,148,151,152
    @Composable
    fun Row(t: String, s: String, on: Boolean, set: (Boolean) -> Unit) {
        ToggleRow(on, set, Modifier.defaultMinSize(minHeight = 60.dp).padding(horizontal = 16.dp), tint = Theme.teal) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { T(t, style = appFont(15f, FontWeight.Bold)); T(s, style = appFont(13f), color = Theme.sub) }
        }
    }
    // ios: Views/Wave/WaveFeedback.swift:134,139,140
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row("게임 소리", "파도 타기 효과음 · 무음 모드면 안 나요", sound) { sound = it }   // ios: Views/Wave/WaveFeedback.swift:135
        HDivider(Theme.line)   // ios: Views/Wave/WaveFeedback.swift:136
        Row("게임 진동", "탈 때 톡, 내릴 때 탁", haptic) { haptic = it }   // ios: Views/Wave/WaveFeedback.swift:137
    }
}
