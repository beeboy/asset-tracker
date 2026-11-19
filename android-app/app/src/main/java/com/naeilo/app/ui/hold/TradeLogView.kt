package com.naeilo.app.ui.hold

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Day
import com.naeilo.app.core.calc.CalmEvent
import com.naeilo.app.core.calc.CalmEvent.Act
import com.naeilo.app.data.Symbol
import com.naeilo.app.data.persists
import com.naeilo.app.net.Sync
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ConfirmSheet
import com.naeilo.app.ui.DialogAction
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.Segmented
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.SheetTitleBar
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.plainClickable
import java.time.LocalDate

/** 종목 상세 아래: 이 종목 매매 기록 (내 투자 습관 기록 중 이 종목 매매만, 최근 것부터). 줄을 누르면 고치기·지우기 */
@Composable
fun TradeLogCard(m: AppModel, sym: Symbol) {   // ios: Views/TradeLogView.swift:4,5,6,10
    var shown by remember { mutableStateOf(5) }   // ios: Views/TradeLogView.swift:7
    var pick by remember { mutableStateOf<CalmEvent?>(null) }   // ios: Views/TradeLogView.swift:8
    val ev = m.calm.events.filter { it.sym == sym.id && it.isTrade }.reversed()   // ios: Views/TradeLogView.swift:11
    if (ev.isEmpty()) return   // ios: Views/TradeLogView.swift:12
    Card {   // ios: Views/TradeLogView.swift:13
        Row(Modifier.fillMaxWidth()) {   // ios: Views/TradeLogView.swift:14 firstTextBaseline
            T("매매 기록", Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold))   // ios: Views/TradeLogView.swift:15
            Spacer(Modifier.weight(1f))   // ios: Views/TradeLogView.swift:16
            T("${ev.size}건", Modifier.alignByBaseline(), style = appFont(12f), color = Theme.muted)   // ios: Views/TradeLogView.swift:17
        }
        for (e in ev.take(shown)) {   // ios: Views/TradeLogView.swift:19
            // ios: Views/TradeLogView.swift:20,21,29,31
            Row(Modifier.fillMaxWidth().plainClickable { pick = e }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/TradeLogView.swift:22
                    T("${if (e.buy) "샀어요" else "팔았어요"} · ${e.label}", style = appFont(13f), color = Theme.ink)   // Views/TradeLogView.swift:23 (검사 예외: 옮김)
                    T(Day.md(e.day), style = appFont(12f), color = Theme.muted)   // ios: Views/TradeLogView.swift:24
                }
                Sym("chevron.right", 11f, Theme.muted)   // ios: Views/TradeLogView.swift:26,27
            }
        }
        if (ev.size > shown) {   // ios: Views/TradeLogView.swift:33
            // ios: Views/TradeLogView.swift:34,35
            T("더 보기 (${ev.size - shown}건 남음)", Modifier.plainClickable { shown += 10 }, style = appFont(13f, FontWeight.SemiBold), color = Theme.teal)
        }
    }
    // ios: Views/TradeLogView.swift:38,39,88
    Sheet(pick != null, onDismiss = { pick = null }, medium = true) { pick?.let { TradeEditSheet(m, it) { pick = null } } }
}

/** 산/판에 맞는 분류 (기다림은 매매가 아니라 뺀다) */
// ios: Views/TradeLogView.swift:56
fun tradeActs(buy: Boolean): List<Act> = if (buy) listOf(Act.rule, Act.fresh, Act.chase, Act.avgDown) else listOf(Act.rule, Act.trim, Act.switchOut)

/** 매매 기록 하나 고치기: 날짜·샀어요/팔았어요·분류, 지우기 */
@Composable
fun TradeEditSheet(m: AppModel, old: CalmEvent, dismiss: () -> Unit) {   // ios: Views/TradeLogView.swift:46,47,48,49,58
    // ios: Views/TradeLogView.swift:50,89,90 (처음 값은 onAppear 대신 remember 로)
    var day by remember { mutableStateOf(runCatching { LocalDate.parse(old.day) }.getOrNull() ?: LocalDate.now()) }
    var buy by remember { mutableStateOf(old.buy) }   // ios: Views/TradeLogView.swift:51,91
    var act by remember { mutableStateOf(if (old.act in tradeActs(old.buy)) old.act else Act.rule) }   // ios: Views/TradeLogView.swift:52,92
    var askDel by remember { mutableStateOf(false) }   // ios: Views/TradeLogView.swift:53
    val ctx = LocalContext.current
    // ios: Views/TradeLogView.swift:59,86,87
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SheetTitleBar("매매 기록 고치기") { dismiss() }   // ios: Views/TradeLogView.swift:60
        // DatePicker 는 플랫폼 날짜 창으로 (오늘까지만)
        // ios: Views/TradeLogView.swift:61
        Row(Modifier.fillMaxWidth().plainClickable {
            DatePickerDialog(ctx, { _, y, mo, d -> day = LocalDate.of(y, mo + 1, d) }, day.year, day.monthValue - 1, day.dayOfMonth)
                .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }, verticalAlignment = Alignment.CenterVertically) {
            T("날짜", Modifier.weight(1f), style = appFont(15f))
            T(day.toString().replace("-", "."), style = appFont(15f), color = Theme.teal)
        }
        // ios: Views/TradeLogView.swift:62,63,64,66,67
        Segmented(listOf(true to "샀어요", false to "팔았어요"), buy, { b -> buy = b; if (act !in tradeActs(b)) act = Act.rule })
        // 메뉴 고르기는 줄 목록으로 (고른 줄에 체크)
        Column {   // ios: Views/TradeLogView.swift:68
            T("분류", style = appFont(15f))   // ios: Views/TradeLogView.swift:69,70
            for (a in tradeActs(buy)) {   // ios: Views/TradeLogView.swift:71,72,74
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).plainClickable { act = a }, verticalAlignment = Alignment.CenterVertically) {
                    T(CalmEvent.label(a), Modifier.weight(1f), style = appFont(15f), color = if (a == act) Theme.teal else Theme.ink)
                    if (a == act) Sym("checkmark", 13f, Theme.teal)
                }
            }
        }
        // ios: Views/TradeLogView.swift:76,77,78,79
        PrimaryButton("저장") {
            val c = m.calm.copy(); c.edit(old, day = day.toString(), buy = buy, act = act); m.calm = c
            if (m.persists) Sync.shared.changed(m)
            dismiss()
        }
        // ios: Views/TradeLogView.swift:81,82
        Row(Modifier.fillMaxWidth().plainClickable { askDel = true }, horizontalArrangement = Arrangement.Center) {
            T("지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
        }
    }
    // ios: Views/TradeLogView.swift:94,95,96,97,98,100
    ConfirmSheet(askDel, "이 기록을 지울까요?", null, listOf(DialogAction("지우기", destructive = true, onClick = {
        val c = m.calm.copy(); c.remove(old); m.calm = c
        if (m.persists) Sync.shared.changed(m)
        dismiss()
    })), cancel = "취소") { askDel = false }
}
