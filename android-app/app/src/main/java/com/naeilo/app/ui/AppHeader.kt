package com.naeilo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 탭 화면 머리글: 'naeilo' 로고 뒤에 탭 이름 ("naeilo 홈")
@Composable
fun AppHeader(tab: String = "") {   // ios: App.swift:142,143,144
    Row(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}   // ios: App.swift:150
            .padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 4.dp),   // ios: App.swift:151
        horizontalArrangement = Arrangement.spacedBy(6.dp),   // ios: App.swift:145,148 (firstTextBaseline·남는 폭은 오른쪽 Spacer 자리)
    ) {
        T("naeilo", Modifier.alignByBaseline(), style = appFont(20f, FontWeight.Bold).copy(letterSpacing = (-0.3).sp))   // ios: App.swift:146
        if (tab.isNotEmpty()) T(tab, Modifier.alignByBaseline(), style = appFont(20f, FontWeight.Normal), color = Theme.sub)   // ios: App.swift:147
    }
}
