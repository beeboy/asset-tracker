package com.naeilo.app.ui.settings

import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.Store
import com.naeilo.app.core.TossFeed
import com.naeilo.app.core.WChar
import com.naeilo.app.core.WReward
import com.naeilo.app.core.WidgetUnlock
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.focusName
import com.naeilo.app.core.calc.switchRoute
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.data.meUnlocked
import com.naeilo.app.data.voice
import com.naeilo.app.net.Backup
import com.naeilo.app.net.BackupFile
import com.naeilo.app.net.Market
import com.naeilo.app.net.Sync
import com.naeilo.widget.BoardCharView
import com.naeilo.widget.Catalog
import com.naeilo.widget.CharBG
import com.naeilo.widget.CharEntry
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.widget.LocalCharDark
import com.naeilo.widget.GardenCharView
import com.naeilo.widget.HeightCharView
import com.naeilo.widget.HillCharView
import com.naeilo.widget.LadderCharView
import com.naeilo.app.data.Lines
import com.naeilo.app.data.Notifier
import com.naeilo.widget.PuzzleCharView
import com.naeilo.app.ui.remind.RemindOptions
import com.naeilo.widget.TowerCharView
import com.naeilo.widget.TugCharView
import com.naeilo.widget.WidgetCenter
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ConfirmSheet
import com.naeilo.app.ui.DialogAction
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.InputField
import com.naeilo.app.ui.PinnedLayout
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Segmented
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.ToggleRow
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.drawableId
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.start.StartForecastCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// 설정 상세 (시안 26·27판): 알림 · 기기 동기화 · 위젯 · 세금 규칙 · 사용 방법 · 루트.

// ios: Views/SettingsDetail.swift:8,9,10,11
@Composable
private fun SettingsPage(title: String, content: @Composable ColumnScope.() -> Unit) {
    // ios: Views/SettingsDetail.swift:12,17,19,20
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        Screen {
            // ios: Views/SettingsDetail.swift:13
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T(title, style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:14
                content()   // ios: Views/SettingsDetail.swift:15
            }
        }
    }
}

/** 홈 인물 + 말풍선 (루트 바꾸기·개발자 후원 위). 대사는 Lines 의 "<key>.<인물>", 시세가 갱신될 때마다 바뀐다 */
// ios: Views/SettingsDetail.swift:25,26,27,28,29
@Composable
fun FriendSay(m: AppModel, key: String, fallback: String) {
    val who = m.homeFriendShown; val name = m.friendName(who)   // ios: Views/SettingsDetail.swift:30
    // ios: Views/SettingsDetail.swift:31,41
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CharPixel(id = who, width = 42.dp, height = 60.dp, modifier = Modifier.charIdle())   // ios: Views/SettingsDetail.swift:32
        // ios: Views/SettingsDetail.swift:33,37,38,39
        Column(
            Modifier.weight(1f).background(Theme.mintBg, RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            T(name, style = appFont(12f, FontWeight.SemiBold), color = Theme.blue)   // ios: Views/SettingsDetail.swift:34
            T(Lines.pick("$key.${m.voice(who)}") ?: fallback, style = appFont(14f).ls(2f))   // ios: Views/SettingsDetail.swift:35
        }
    }
}

// ios: Views/SettingsDetail.swift:45,46
@Composable
private fun Note(t: String) = T(t, Modifier.fillMaxWidth(), style = appFont(12f).ls(2f), color = Theme.muted)

// ios: Views/SettingsDetail.swift:49
@Composable
private fun OptionChips(label: String, opts: List<Int>, unit: String, sel: Int, pick: (Int) -> Unit) {
    // ios: Views/SettingsDetail.swift:50
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:51
        // ios: Views/SettingsDetail.swift:52
        opts.forEach { n ->
            // ios: Views/SettingsDetail.swift:53,54,55,56,57
            Box(
                Modifier.plainClickable { pick(n) }.defaultMinSize(minHeight = 40.dp)
                    .background(if (sel == n) Theme.teal else Theme.card, RoundedCornerShape(10.dp))
                    .border(2.dp, if (sel == n) Theme.teal else Theme.border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { T("$n$unit", style = appFont(14f, FontWeight.Bold), color = if (sel == n) Color.White else Theme.ink) }
        }
    }
}

// MARK: 알림 — 위에 미리보기 고정

/** 알림 한 가지: 키, 제목, 설명, 미리보기 글 */
private class AlertKind(val k: String, val t: String, val sub: String, val prev: String)

// ios: Views/SettingsDetail.swift:64,65,71
@Composable
fun AlertsView(m: AppModel) {
    val ctx = LocalContext.current
    val active = m.active   // ios: Views/SettingsDetail.swift:67 (scenePhase)
    var perm by remember { mutableStateOf(Notifier.Status.allowed) }   // ios: Views/SettingsDetail.swift:68
    var sent by remember { mutableStateOf(false) }   // ios: Views/SettingsDetail.swift:69
    val scope = rememberCoroutineScope()
    // ios: Views/SettingsDetail.swift:72
    val list = listOf(
        AlertKind("be", "본전 도달", "평가액이 들어간 돈을 넘으면 한 번", "본전에 도착했어요! 평가액이 들어간 돈 ${AppModel.man(m.cost)}을 넘었어요."),   // ios: Views/SettingsDetail.swift:73
        AlertKind("drift", "비중 이탈", "비중 1위 종목 비중이 계획에서 ${m.alertTh}%p 넘게 벗어나면",   // ios: Views/SettingsDetail.swift:74
             "${m.focusName} 비중이 ${AppModel.pct(m.focusWeight)}예요. 계획(${AppModel.pct(m.planWeight)})보다 ${(abs(m.focusWeight - m.planWeight) * 100).roundToInt()}%p 벗어났어요."),   // ios: Views/SettingsDetail.swift:75
        AlertKind("dep", "연말 세금 정리", "12월 1일, 올해 손익과 세금 예시 보기", "올해가 한 달 남았어요. 올해 해외주식 손익과 내년 5월 세금 예시를 앱에서 확인해 보세요."),   // ios: Views/SettingsDetail.swift:76
        AlertKind("morn", "아침 한 줄", "평일 ${m.alertHr}시, 어제의 움직임 한 줄", Notifier.morningLine(m)),   // ios: Views/SettingsDetail.swift:77
    )
    val on = list.filter { m.alerts[it.k] == true }   // ios: Views/SettingsDetail.swift:79
    val prev = on.firstOrNull { it.k == m.alertLast } ?: on.firstOrNull()   // ios: Views/SettingsDetail.swift:80
    val perMonth = (if (m.alerts["drift"] == true) 1 else 0) + (if (m.alerts["dep"] == true) 1 else 0) + (if (m.alerts["morn"] == true) 21 else 0)   // ios: Views/SettingsDetail.swift:81

    // ios: Views/SettingsDetail.swift:140
    LaunchedEffect(active) { perm = Notifier.status() }
    // 알림을 켜는 순간 폰 허용을 묻는다
    // ios: Views/SettingsDetail.swift:142,143
    LaunchedEffect(m.alerts) {
        if (m.alerts.values.contains(true) && perm == Notifier.Status.unknown) { Notifier.request(); perm = Notifier.status(); Notifier.reschedule(m) }
    }

    // ios: Views/SettingsDetail.swift:66 openURL 자리: 폰 설정 화면은 Context 로 연다
    val openURL = ctx
    // ios: Views/SettingsDetail.swift:147,148,157,158
    @Composable
    fun PermCard() {
        Column(Modifier.fillMaxWidth().background(Theme.cream, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(if (perm == Notifier.Status.denied) "폰 알림이 꺼져 있어요" else "폰 알림 허용이 필요해요", style = appFont(15f, FontWeight.Bold))   // 149 (아이폰 → 폰: 예외)
            // ios: Views/SettingsDetail.swift:151 (150 아이폰 → 폰: 예외)
            T(if (perm == Notifier.Status.denied) "폰 설정 > 알림 > naeilo에서 허용해 주세요." else "허용해야 알림이 와요.", style = appFont(13f), color = Theme.sub)
            // ios: Views/SettingsDetail.swift:153,154 (152 아이폰 → 폰: 예외)
            PrimaryButton(title = if (perm == Notifier.Status.denied) "폰 설정 열기" else "알림 허용하기") {
                if (perm == Notifier.Status.denied) runCatching { openURL.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else scope.launch { Notifier.request(); perm = Notifier.status(); Notifier.reschedule(m) }
            }
        }
    }

    // ios: Views/SettingsDetail.swift:83,109,138,139
    PinnedLayout(header = {
        // ios: Views/SettingsDetail.swift:84,108
        Column(Modifier.fillMaxWidth().background(Theme.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("알림", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:85
            // ios: Views/SettingsDetail.swift:86,88
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("알림 미리보기", Modifier.weight(1f), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/SettingsDetail.swift:87
                T("켠 알림 ${on.size}개" + (if (perMonth > 0) " · 한 달 약 ${perMonth}번" else ""), style = appFont(12f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:89
            }
            // ios: Views/SettingsDetail.swift:91,105,106
            Row(Modifier.fillMaxWidth().background(hexD(0xEEF1F5, 0x222C36), RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 설정에서 고른 앱 아이콘 (알림에도 같은 그림이 붙는다)
                // ios: Views/SettingsDetail.swift:93,94,95
                val icon = AppIconPicker.icons.firstOrNull { it.id == AppIconPicker.alternateIconName(ctx) }?.prev ?: "iconprev_star"
                androidx.compose.foundation.Image(androidx.compose.ui.graphics.ImageBitmap.imageResource(drawableId(ctx, icon)), null, Modifier.size(36.dp, 36.dp).clip(RoundedCornerShape(9.dp)))
                // ios: Views/SettingsDetail.swift:96
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // ios: Views/SettingsDetail.swift:97,99
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(prev?.t ?: "알림 꺼짐", Modifier.weight(1f), style = appFont(14f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:98
                        T(if (prev == null) "" else if (prev.k == "morn") "오전 ${m.alertHr}:00" else "지금", style = appFont(12f), color = Theme.muted)   // ios: Views/SettingsDetail.swift:100
                    }
                    T(prev?.prev ?: "켠 알림이 없어요. 아래에서 하나 이상 켜 보세요.", style = appFont(13f))   // ios: Views/SettingsDetail.swift:102
                }
            }
        }
        HDivider(Color(0x4D3C3C43))
    }) {
        // ios: Views/SettingsDetail.swift:110,136
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (perm != Notifier.Status.allowed) PermCard()   // ios: Views/SettingsDetail.swift:111
            // ios: Views/SettingsDetail.swift:112
            list.forEach { a ->
                val isOn = m.alerts[a.k] == true   // ios: Views/SettingsDetail.swift:113
                // ios: Views/SettingsDetail.swift:114
                Card(padding = 14.dp) {
                    // ios: Views/SettingsDetail.swift:115,116,121
                    ToggleRow(isOn, { v -> m.alerts = m.alerts + (a.k to v); if (v) m.alertLast = a.k }, tint = Theme.teal) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            T(a.t, style = appFont(16f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:117
                            T(a.sub, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:118
                        }
                    }
                    if (isOn && a.k == "drift") OptionChips("기준", listOf(3, 5, 10), "%p", m.alertTh) { m.alertTh = it }   // ios: Views/SettingsDetail.swift:122
                    if (isOn && a.k == "morn") OptionChips("시간", listOf(7, 8, 9), "시", m.alertHr) { m.alertHr = it }   // ios: Views/SettingsDetail.swift:123
                }
            }
            // ios: Views/SettingsDetail.swift:126
            if (perm == Notifier.Status.allowed && on.isNotEmpty()) {
                // ios: Views/SettingsDetail.swift:127,128,129,130,131
                Box(Modifier.fillMaxWidth().plainClickable { scope.launch { Notifier.test(m); sent = true } }.defaultMinSize(minHeight = 44.dp)
                    .border(1.5.dp, Theme.teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    T(if (sent) "5초 뒤에 시험 알림이 와요" else "시험 알림 받아 보기", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)
                }
            }
            Note("알림은 하루 한 번까지 와요. 아침 한 줄은 앱을 마지막으로 연 때의 숫자예요.")   // ios: Views/SettingsDetail.swift:133
            Card(padding = 14.dp) { RemindOptions() }   // ios: Views/SettingsDetail.swift:134
        }
    }
}

// MARK: 기기 옮기기 — 기본은 파일 하나로, 기기 동기화는 켠 사람만 (같은 비밀번호의 폰끼리)

// ios: Views/SettingsDetail.swift:164,165,193
@Composable
fun SyncView(m: AppModel) {
    var pw by remember { mutableStateOf("") }   // ios: Views/SettingsDetail.swift:166
    var pw2 by remember { mutableStateOf("") }   // ios: Views/SettingsDetail.swift:167
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsDetail.swift:168
    var devOpen by remember { mutableStateOf(false) }   // ios: Views/SettingsDetail.swift:169
    var token by remember { mutableStateOf("") }   // ios: Views/SettingsDetail.swift:170
    var pending by remember { mutableStateOf<Backup?>(null) }   // ios: Views/SettingsDetail.swift:174
    var fileMsg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsDetail.swift:175
    val scope = rememberCoroutineScope()
    val sync = Sync.shared   // ios: Views/SettingsDetail.swift:191
    /** 토스 시세 상태 (주소를 넣은 개발자 기기만): 마지막 성공 시각, 마지막 실패 이유 */
    // ios: Views/SettingsDetail.swift:177
    val tossStatus: String = run {
        Market.shared.updatedAt     // ios: Views/SettingsDetail.swift:178 시세를 새로 받으면 다시 그린다
        val t = TossFeed.okAt   // ios: Views/SettingsDetail.swift:179
        var s = "오픈 API: "   // ios: Views/SettingsDetail.swift:180
        if (TossFeed.base == null) return@run s + "주소 없음"   // ios: Views/SettingsDetail.swift:181
        // ios: Views/SettingsDetail.swift:182,183,184,185,186
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s += "마지막 성공 $f"
        } else { s += "아직 성공 없음" }   // ios: Views/SettingsDetail.swift:187
        TossFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsDetail.swift:188
        s   // ios: Views/SettingsDetail.swift:189
    }

    // ios: Views/SettingsDetail.swift:295
    val statusText: String = when (val st = sync.state) {
        Sync.State.off -> "같은 비밀번호 폰끼리 · 앱 닫을 때 올림"   // ios: Views/SettingsDetail.swift:296,297
        Sync.State.idle -> if (sync.isDev) "같은 GitHub 계정 기기끼리" else "같은 비밀번호 폰끼리 · 앱 닫을 때 올림"   // ios: Views/SettingsDetail.swift:298
        Sync.State.working -> "맞추는 중…"   // ios: Views/SettingsDetail.swift:299
        // ios: Views/SettingsDetail.swift:300,301,302
        is Sync.State.ok -> {
            val f = DateTimeFormatter.ofPattern("a h:mm", AppModel.dateLocale).withZone(ZoneId.systemDefault())
            "마지막: ${f.format(st.at)}"
        }
        is Sync.State.failed -> "실패: ${st.why}"   // ios: Views/SettingsDetail.swift:303
    }

    // 파일로 저장·불러오기: 안드로이드 기본 문서 선택기(SAF). 저장은 파일 만들기, 불러오기는 JSON 열기
    val ctx = LocalContext.current
    // ios: Views/SettingsDetail.swift:171,173,268,269,270,271 (fileExporter)
    val exporting = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val exportDoc = BackupFile.data(Backup.of(m))
        fileMsg = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(exportDoc) } }
            .fold({ "저장함 · 종목 ${m.holdings.size}개" }, { e -> "저장 실패: ${e.localizedMessage}" })
    }
    // ios: Views/SettingsDetail.swift:172,274,275,276 (fileImporter, .json)
    val importing = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val d = try { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } } catch (e: Exception) { fileMsg = "열기 실패: ${e.localizedMessage}"; return@rememberLauncherForActivityResult }   // ios: Views/SettingsDetail.swift:278
        try { pending = BackupFile.read(d) } catch (e: Exception) { fileMsg = "naeilo 파일이 아니에요" }   // ios: Views/SettingsDetail.swift:277
    }

    // ios: Views/SettingsDetail.swift:307
    @Composable
    fun ConflictCard(c: Sync.Remote) {
        val f = DateTimeFormatter.ofPattern("M월 d일 a h:mm", AppModel.dateLocale).withZone(ZoneId.systemDefault())   // ios: Views/SettingsDetail.swift:308
        val n = (c.state["holdings"]?.value as? List<*>)?.size ?: 0   // ios: Views/SettingsDetail.swift:309
        // ios: Views/SettingsDetail.swift:310
        Card {
            T("다른 폰 값이 있어요", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:311
            // ios: Views/SettingsDetail.swift:312,313
            T("${f.format(Instant.ofEpochMilli((c.at / 1000 * 1000).toLong()))} 저장 · 종목 ${n}개 · 어느 쪽으로?", style = appFont(14f))
            PrimaryButton(title = "다른 폰 값으로") { scope.launch { sync.resolve(useRemote = true, m) } }   // ios: Views/SettingsDetail.swift:314
            // ios: Views/SettingsDetail.swift:315,316,317
            Box(Modifier.fillMaxWidth().plainClickable { scope.launch { sync.resolve(useRemote = false, m) } }.defaultMinSize(minHeight = 48.dp).border(2.dp, Theme.teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                T("이 폰 값 보내기", style = appFont(15f, FontWeight.SemiBold), color = Theme.teal)
            }
        }
    }

    /** 파일 하나로 옮기기 (동기화 없이): 저장하기·불러오기 */
    // ios: Views/SettingsDetail.swift:255,256
    @Composable
    fun FileCard() = Card {
        T("파일로 옮기기", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:257
        T("종목·미션 진행을 파일 하나로 · 새 폰에서 불러오기", style = appFont(14f).ls(3f))   // ios: Views/SettingsDetail.swift:258,259
        PrimaryButton(title = "파일로 저장") { exporting.launch(Backup.fileName()) }   // ios: Views/SettingsDetail.swift:260
        // ios: Views/SettingsDetail.swift:261,262,263
        Box(Modifier.fillMaxWidth().plainClickable { importing.launch(arrayOf("application/json")) }.defaultMinSize(minHeight = 48.dp).border(2.dp, Theme.teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            T("파일 불러오기", style = appFont(15f, FontWeight.SemiBold), color = Theme.teal)
        }
        fileMsg?.let { T(it, style = appFont(13f), color = Theme.sub) }   // ios: Views/SettingsDetail.swift:264
        T("암호화 안 됨 · 남에게 보내지 마세요", style = appFont(12f), color = Theme.muted)   // ios: Views/SettingsDetail.swift:265,266
        // ios: Views/SettingsDetail.swift:281,282,283,284,285,286,288,289,290 (291 은 검사 예외: 같은 글로 옮김)
        val b = pending
        val f = DateTimeFormatter.ofPattern("M월 d일 a h:mm", AppModel.dateLocale).withZone(ZoneId.systemDefault())
        ConfirmSheet(b != null, "이 파일로 바꿀까요?",
            "${b?.let { f.format(it.at) } ?: ""} 저장 · 종목 ${b?.holdings?.size ?: 0}개 · 이 폰 종목·미션 진행이 바뀌어요" + (if (sync.isOn) " · 동기화로 다른 폰에도" else ""),
            listOf(DialogAction("불러오기", onClick = {
                val p = b ?: return@DialogAction
                p.restore(m)
                fileMsg = "불러옴 · 종목 ${p.holdings.size}개"
                pending = null
            }))) { pending = null }
    }

    val on = sync.isOn   // ios: Views/SettingsDetail.swift:194
    // ios: Views/SettingsDetail.swift:195
    SettingsPage(title = "기기 옮기기") {
        // 기본은 파일 하나로 옮기기. 동기화는 켠 사람만 쓰고, 올리기는 앱을 닫을 때 한 번 (MainActivity.onStop)
        FileCard()   // ios: Views/SettingsDetail.swift:197
        sync.conflict?.let { ConflictCard(it) }   // ios: Views/SettingsDetail.swift:198
        // ios: Views/SettingsDetail.swift:199
        Card {
            // ios: Views/SettingsDetail.swift:200,201,202,203
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("기기 동기화", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))
                T(if (on) "켜짐" else "꺼짐", style = appFont(13f, FontWeight.SemiBold), color = if (on) Theme.teal else Theme.muted)
            }
            T(statusText, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:205
            // ios: Views/SettingsDetail.swift:206
            if (on) {
                PrimaryButton(title = if (sync.state == Sync.State.working) "맞추는 중…" else "지금 맞추기") { scope.launch { sync.pull(m) } }   // ios: Views/SettingsDetail.swift:207
                // ios: Views/SettingsDetail.swift:208,209,210
                Box(Modifier.fillMaxWidth().plainClickable { askOff = true }.defaultMinSize(minHeight = 48.dp).border(2.dp, Theme.border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    T("동기화 끄기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
                }
                // ios: Views/SettingsDetail.swift:211,212,213
                ConfirmSheet(askOff, "이 폰 동기화를 끌까요?",
                    if (sync.isDev) "이 폰 값은 그대로 · 토큰은 지워요" else "이 폰 값은 그대로 · 다시 켜려면 같은 비밀번호",
                    listOf(DialogAction("끄기", destructive = true, onClick = { sync.turnOff() }))) { askOff = false }
            } else {   // ios: Views/SettingsDetail.swift:214
                val ok = pw.length >= 10 && pw == pw2   // ios: Views/SettingsDetail.swift:215
                InputField(pw, { pw = it }, "비밀번호 (10자 이상)", Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)   // ios: Views/SettingsDetail.swift:216,217
                InputField(pw2, { pw2 = it }, "한 번 더", Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)   // ios: Views/SettingsDetail.swift:218,219
                if (pw.isNotEmpty() && pw.length < 10) T("10자 이상", style = appFont(12f), color = Theme.up)   // ios: Views/SettingsDetail.swift:220
                else if (pw2.isNotEmpty() && pw != pw2) T("비밀번호가 달라요", style = appFont(12f), color = Theme.up)   // ios: Views/SettingsDetail.swift:221
                // ios: Views/SettingsDetail.swift:222
                PrimaryButton(title = if (sync.state == Sync.State.working) "준비 중…" else "켜기", color = if (ok) Theme.teal else Theme.muted) {
                    if (!ok || sync.state == Sync.State.working) return@PrimaryButton   // ios: Views/SettingsDetail.swift:223
                    val p = pw; pw = ""; pw2 = ""   // ios: Views/SettingsDetail.swift:224
                    scope.launch { sync.turnOn(password = p, model = m) }   // ios: Views/SettingsDetail.swift:225
                }
            }
        }
        // ios: Views/SettingsDetail.swift:229
        if (!on) {
        // DisclosureGroup: 누르면 펼쳐지는 '개발자용'
        // ios: Views/SettingsDetail.swift:230,245,246
        Column {
            val turn by animateFloatAsState(if (devOpen) 90f else 0f, label = "dis")
            Row(Modifier.fillMaxWidth().plainClickable { devOpen = !devOpen }.defaultMinSize(minHeight = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                T("개발자용", Modifier.weight(1f), style = appFont(14f, FontWeight.SemiBold), color = Theme.sub)
                Sym("chevron.right", 14f, Theme.sub, Modifier.rotate(turn))
            }
            // ios: Views/SettingsDetail.swift:231,244
            AnimatedVisibility(devOpen) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("GitHub 토큰을 넣은 기기끼리 비밀번호 대신 토큰으로 맞춰요.", style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:232,233
                    InputField(token, { token = it }, "GitHub 토큰", Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)   // ios: Views/SettingsDetail.swift:234,235
                    // ios: Views/SettingsDetail.swift:236
                    PrimaryButton(title = if (sync.state == Sync.State.working) "확인 중…" else "토큰으로 연결", color = if (token.isEmpty()) Theme.muted else Theme.teal) {
                        if (token.isEmpty() || sync.state == Sync.State.working) return@PrimaryButton   // ios: Views/SettingsDetail.swift:237
                        val t = token; token = ""   // ios: Views/SettingsDetail.swift:238
                        scope.launch { sync.turnOnDev(token = t, model = m) }   // ios: Views/SettingsDetail.swift:239
                    }
                    T("토큰은 이 폰 보관함에만 둬요. 개발자 칸은 암호화하지 않아요.", style = appFont(12f), color = Theme.muted)   // ios: Views/SettingsDetail.swift:242 (241 키체인 → 보관함: 예외)
                }
            }
        }
        }
        // 토스 상태는 동기화 켜짐·꺼짐과 상관없이 늘 보인다
        T(tossStatus, style = appFont(12f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:249
        Note("서버엔 암호화한 값만 · 비밀번호는 저장 안 함")   // ios: Views/SettingsDetail.swift:250
    }
}

// MARK: 위젯 — 받은 위젯과 인물 위젯 미리보기 (미션 진행대로 열림)
// 홈 화면에 놓는 일은 폰이 하므로 여기서는 넣고 빼지 않는다. 무엇이 열렸는지, 어떻게 보이는지, 언제 열리는지만 보여 준다.

// ios: Views/SettingsDetail.swift:325,326 (extension AppModel 의 WidgetInfo)
class WidgetInfo(val id: String, val name: String, val desc: String, val size: String, val ok: Boolean, val how: String)
/** 기본 위젯 (위젯 추가 화면과 같은 이름·순서, Catalog). 구독 중이거나 7일 무료 동안 모두 열린다 */
// ios: Views/SettingsDetail.swift:328,329
val AppModel.baseWidgets: List<WidgetInfo> get() =
    Catalog.base.map { WidgetInfo(id = it.kind, name = it.name, desc = it.desc, size = it.size, ok = Store.isUnlocked(it.kind), how = WidgetUnlock.how(it.kind)) }

/** 설정 > 위젯 > 금액 가리기: 금액을 누르면 모든 위젯 함께 / 누른 위젯만 */
// ios: Views/SettingsDetail.swift:334,338
@Composable
fun AmountHideCard() {
    var each by remember { mutableStateOf(Store.hideEach) }   // ios: Views/SettingsDetail.swift:335
    var lockPrivate by remember { mutableStateOf(Store.lockPrivate) }   // ios: Views/SettingsDetail.swift:336
    // ios: Views/SettingsDetail.swift:351
    fun setEach(v: Boolean) {
        each = v
        // 방식을 바꿔도 지금 가려진 상태는 그대로 둔다
        val kinds = Catalog.base.map { it.kind }.sorted()   // ios: Views/SettingsDetail.swift:353
        if (v) Store.hiddenKinds = if (Store.hideAll) kinds else emptyList() else Store.hideAll = Store.hiddenKinds.containsAll(kinds)   // ios: Views/SettingsDetail.swift:354
        Store.hideEach = v   // ios: Views/SettingsDetail.swift:355
        WidgetCenter.reloadAllTimelines()   // ios: Views/SettingsDetail.swift:356
    }
    // ios: Views/SettingsDetail.swift:358
    fun setLock(v: Boolean) {
        lockPrivate = v
        Store.lockPrivate = v   // ios: Views/SettingsDetail.swift:359
        WidgetCenter.reloadAllTimelines()   // ios: Views/SettingsDetail.swift:360
    }
    // ios: Views/SettingsDetail.swift:339,348,349,350
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("금액을 누르면", style = appFont(14f, FontWeight.SemiBold))   // ios: Views/SettingsDetail.swift:340
        // ios: Views/SettingsDetail.swift:341,342,343,345 (분할, 이름 "금액을 누르면")
        Segmented(listOf(false to "모든 위젯 함께", true to "누른 위젯만"), each, ::setEach, Modifier.semantics { contentDescription = tr("금액을 누르면") })
        // ios: Views/SettingsDetail.swift:346
        ToggleRow(lockPrivate, ::setLock, Modifier.padding(top = 4.dp), tint = Theme.teal) { T("잠긴 동안 잠금화면 금액 가리기", style = appFont(14f)) }
    }
}

// ios: Views/SettingsDetail.swift:365,366,372
@Composable
fun WidgetPickView(m: AppModel) {
    val prefs = Defaults.prefs
    var dark by remember { mutableStateOf(prefs.getBoolean("cpDark", false)) }   // ios: Views/SettingsDetail.swift:367
    var who by remember { mutableStateOf(prefs.getString("cpWho", null)) }   // ios: Views/SettingsDetail.swift:368

    val reward: WReward = Store.read(WReward.serializer(), "reward.json") ?: WReward.sample   // ios: Views/SettingsDetail.swift:370

    val base = m.baseWidgets; val friendsOn = Shelter.friends.indices.count { m.friendOn(it) }   // ios: Views/SettingsDetail.swift:373
    val sel = who ?: m.homeFriendShown   // ios: Views/SettingsDetail.swift:374
    val fi = Shelter.friends.indexOfFirst { it.id == sel }.let { if (it < 0) 0 else it }; val on = if (sel == "me") m.meUnlocked else m.friendOn(fi)   // ios: Views/SettingsDetail.swift:375

    // ios: Views/SettingsDetail.swift:481
    @Composable
    fun androidx.compose.foundation.layout.RowScope.Stat(k: String, v: String) {
        // ios: Views/SettingsDetail.swift:482,486,487,488
        Column(Modifier.weight(1f).background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(k, style = appFont(12f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:483
            T(v + " 열림", style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:484
        }
    }

    // ios: Views/SettingsDetail.swift:491,492
    @Composable
    fun Caption(t: String, v: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { v(); T(t, style = appFont(12f), color = Theme.sub) }
    }

    /** 큰 위젯 크기 그대로 (338×354 를 폭에 맞춰 줄인다) */
    // ios: Views/SettingsDetail.swift:496
    @Composable
    @Suppress("unused")
    fun LargeTile(e: CharEntry, w: androidx.compose.ui.unit.Dp, v: @Composable () -> Unit) {
        val k = min(1f, w.value / 338)   // ios: Views/SettingsDetail.swift:497
        // ios: Views/SettingsDetail.swift:498,503,504
        Box(Modifier.size(w, (354 * k).dp).clip(RoundedCornerShape((22 * k).dp)), contentAlignment = Alignment.Center) {
            CharBG(c = e.c, locked = e.locked, dark = dark)   // ios: Views/SettingsDetail.swift:499
            // ios: Views/SettingsDetail.swift:500,501
            Box(Modifier.requiredSize(338.dp, 354.dp).graphicsLayer { scaleX = k; scaleY = k }.padding(16.dp)) { CompositionLocalProvider(LocalCharDark provides dark) { Fg(Color.White) { v() } } }
        }
    }

    /** 위젯 크기 그대로 (바탕 + 위젯 안쪽 여백) */
    // ios: Views/SettingsDetail.swift:508
    @Composable
    fun Tile(e: CharEntry, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, v: @Composable () -> Unit) {
        val k = min(1f, h.value / 158)   // ios: Views/SettingsDetail.swift:509
        // ios: Views/SettingsDetail.swift:510,515,516
        Box(Modifier.size(w, h).clip(RoundedCornerShape((22 * k).dp)), contentAlignment = Alignment.Center) {
            CharBG(c = e.c, locked = e.locked, dark = dark)   // ios: Views/SettingsDetail.swift:511
            // ios: Views/SettingsDetail.swift:512,513
            Box(Modifier.requiredSize((w.value / k).dp, 158.dp).graphicsLayer { scaleX = k; scaleY = k }.padding(16.dp)) { CompositionLocalProvider(LocalCharDark provides dark) { Fg(Color.White) { v() } } }
        }
    }

    // ios: Views/SettingsDetail.swift:376,475,477,478
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen {
            // ios: Views/SettingsDetail.swift:377
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("위젯", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:378
                // ios: Views/SettingsDetail.swift:380 (394 '홈 화면 길게 누르기 → +' 는 안드로이드 위젯 추가 순서로: 예외)
                T("미션을 마치면 열려요. 홈 화면 길게 누르기 → 위젯 → naeilo", style = appFont(14f), color = Theme.sub)
                // ios: Views/SettingsDetail.swift:381
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stat("기본 위젯", "${base.count { it.ok }}/${base.size}")   // ios: Views/SettingsDetail.swift:382
                    Stat("인물", "$friendsOn/${Shelter.friends.size}")   // ios: Views/SettingsDetail.swift:383
                }

                // 인물 위젯: 만난 인물을 고르면 여덟 가지 모양을 바로 본다 (못 만난 친구는 목록에 없다)
                T("인물 위젯", Modifier.padding(top = 6.dp), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:387
                // ios: Views/SettingsDetail.swift:388,389
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // ios: Views/SettingsDetail.swift:390
                    Shelter.friends.withIndex().filter { m.friendOn(it.index) }.forEach { (i, f) ->
                        val ok = m.friendOn(i); val cur = f.id == sel   // ios: Views/SettingsDetail.swift:391
                        // ios: Views/SettingsDetail.swift:392,398,399,400,402,403
                        Column(
                            Modifier.plainClickable { who = f.id; prefs.edit().putString("cpWho", f.id).apply() }
                                .semantics(mergeDescendants = true) { contentDescription = tr(if (ok) f.name else "잠긴 인물, 인터미션 ${i}주차") }
                                .size(72.dp, 86.dp)
                                .background(if (cur) Theme.mintBg else Theme.card, RoundedCornerShape(12.dp))
                                .border(2.dp, if (cur) Theme.teal else Theme.border, RoundedCornerShape(12.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),   // ios: Views/SettingsDetail.swift:393
                        ) {
                            PixelN((if (ok) "spr_" else "sil_") + f.id, width = 30.dp, height = 42.dp)   // ios: Views/SettingsDetail.swift:394
                            T(if (ok) f.name else "???", style = appFont(12f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:395
                            T(if (ok) (if (f.id == m.homeFriendShown) "홈에 있음" else "만남") else "인터미션 ${i}주차", style = appFont(10f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:396
                        }
                    }
                    // 내 캐릭터: 열렸을 때만
                    // ios: Views/SettingsDetail.swift:406
                    if (m.meUnlocked) {
                        // ios: Views/SettingsDetail.swift:407,413,414,415,417
                        Column(
                            Modifier.plainClickable { who = "me"; prefs.edit().putString("cpWho", "me").apply() }
                                .size(72.dp, 86.dp)
                                .background(if (sel == "me") Theme.mintBg else Theme.card, RoundedCornerShape(12.dp))
                                .border(2.dp, if (sel == "me") Theme.teal else Theme.border, RoundedCornerShape(12.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),   // ios: Views/SettingsDetail.swift:408
                        ) {
                            CharPixel(id = "me", width = 30.dp, height = 42.dp)   // ios: Views/SettingsDetail.swift:409
                            T(m.friendName("me"), style = appFont(12f, FontWeight.Bold), maxLines = 1)   // ios: Views/SettingsDetail.swift:410
                            T(if (m.homeFriendShown == "me") "홈에 있음" else "내 캐릭터", style = appFont(10f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:411
                        }
                    }
                }
                // ios: Views/SettingsDetail.swift:421
                Segmented(listOf(false to "기본", true to "다크"), dark, { dark = it; prefs.edit().putBoolean("cpDark", it).apply() })
                val c = WChar.of(if (on) sel else "seri"); val e = CharEntry(r = reward, c = c, locked = false)   // ios: Views/SettingsDetail.swift:422
                // ios: Views/SettingsDetail.swift:423,444 (폭 : 높이 = 1 : 2.7, 안드로이드는 칸이 세로로 길다)
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1 / 2.7f)) {
                    val w = (maxWidth - 12.dp) / 2   // ios: Views/SettingsDetail.swift:424
                    // ios: Views/SettingsDetail.swift:425
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // ios: Views/SettingsDetail.swift:426
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Caption("자산 언덕") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { HillCharView(e = e) } }   // ios: Views/SettingsDetail.swift:427
                            Caption("본전 사다리") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { LadderCharView(e = e) } }   // ios: Views/SettingsDetail.swift:428
                        }
                        // ios: Views/SettingsDetail.swift:430
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Caption("블록 탑") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { TowerCharView(e = e) } }   // ios: Views/SettingsDetail.swift:431
                            Caption("전광판") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { BoardCharView(e = e) } }   // ios: Views/SettingsDetail.swift:432
                        }
                        // ios: Views/SettingsDetail.swift:434
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Caption("키 재기") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { HeightCharView(e = e) } }   // ios: Views/SettingsDetail.swift:435
                            Caption("퍼즐 맞추기") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { PuzzleCharView(e = e) } }   // ios: Views/SettingsDetail.swift:436
                        }
                        // ios: Views/SettingsDetail.swift:438
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Caption("꽃밭") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { GardenCharView(e = e) } }   // ios: Views/SettingsDetail.swift:439
                            Caption("줄다리기") { Tile(e, w = w, h = w * com.naeilo.widget.tileRatio) { TugCharView(e = e) } }   // ios: Views/SettingsDetail.swift:440
                        }
                    }
                }
                Note("인물은 위젯을 길게 눌러 '위젯 편집'에서 골라요.")   // ios: Views/SettingsDetail.swift:445

                // 기본 위젯: 앱 시작 단계로 열림
                T("기본 위젯", Modifier.padding(top = 6.dp), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:448
                // ios: Views/SettingsDetail.swift:449,466,467
                Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    // ios: Views/SettingsDetail.swift:450
                    base.forEachIndexed { i, w ->
                        if (i > 0) HDivider(Theme.line)   // ios: Views/SettingsDetail.swift:451
                        // ios: Views/SettingsDetail.swift:452,462,463
                        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.defaultMinSize(minHeight = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // ios: Views/SettingsDetail.swift:453,454
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Sym(if (w.ok) "checkmark.circle.fill" else "lock.fill", 18f, if (w.ok) Theme.teal else Theme.muted, scaled = false) }
                            // ios: Views/SettingsDetail.swift:455,460
                            Column(Modifier.weight(1f).padding(end = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                T(w.name, style = appFont(16f, FontWeight.SemiBold), color = if (w.ok) Theme.ink else Theme.muted)   // ios: Views/SettingsDetail.swift:456
                                T(w.size + " · " + (if (w.ok) w.desc else w.how), style = appFont(12f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:457,458
                            }
                        }
                    }
                }
                Note("못 받은 위젯은 잠긴 모습으로 보여요.")   // ios: Views/SettingsDetail.swift:468

                // 금액 가리기: 위젯에서 금액을 누르면 •••• 로 가려진다. 함께/따로는 Store.hideEach
                T("금액 가리기", Modifier.padding(top = 6.dp), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:471
                AmountHideCard()   // ios: Views/SettingsDetail.swift:472
                Note("금액을 누르면 가려지고, 다시 누르면 보여요. 잠금화면 위젯은 폰이 잠겨 있는 동안 금액을 가리고, 얼굴이나 지문으로 풀면 보여요.")   // ios: Views/SettingsDetail.swift:473
            }
        }
    }
}

// MARK: 세금 규칙

/** 세금 규칙 화면의 셈 (숫자 맞추기 E 가 같이 쓴다) */
object TaxCalc {
    fun tax(G: Double): Double = max(0.0, G - 250) * 0.22   // ios: Views/SettingsDetail.swift:527
    fun won0(x: Double): String = cformat("%,d", x.roundToLong()) + "만원"   // ios: Views/SettingsDetail.swift:528
}

// ios: Views/SettingsDetail.swift:522,523,526
@Composable
fun TaxRulesView(m: AppModel) {
    var gain by remember { mutableStateOf(m.taxGain.toInt().toString()) }   // ios: Views/SettingsDetail.swift:524,572
    val G = m.taxGain; val tax = TaxCalc.tax(G)
    val won0 = TaxCalc::won0
    // ios: Views/SettingsDetail.swift:529
    SettingsPage(title = "세금 규칙") {
        // 세금 규칙은 아직 대한민국 거주자만: 다른 나라에 살면 준비 중이라고 알리고 한국 기준을 보여 준다
        // ios: Views/SettingsDetail.swift:531,532,536,537,538
        Row(Modifier.plainClickable { m.settingsPath = m.settingsPath + SettingsRoute.residence }.defaultMinSize(minHeight = 40.dp).background(Theme.teal, CapsuleShape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            T("${m.residenceName} 거주자", style = appFont(14f, FontWeight.Bold), color = Color.White)   // ios: Views/SettingsDetail.swift:533
            Sym("chevron.right", 12f, Color.White)   // ios: Views/SettingsDetail.swift:534
        }
        // ios: Views/SettingsDetail.swift:539
        if (m.residence != "KR") {
            T("${m.residenceName} 세금 규칙은 준비 중이에요. 아래는 대한민국 기준이에요.", style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:540
        }
        // ios: Views/SettingsDetail.swift:542,547,548
        Fg(Color.White) {
            Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("내년 5월에 낼 해외주식 세금 (예상)", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/SettingsDetail.swift:543
                T(won0(tax), style = appFont(30f, FontWeight.Bold), roll = won0(tax))   // ios: Views/SettingsDetail.swift:544
                T(if (G > 250) "(${won0(G)} − 기본공제 250만원) × 22%" else "이익이 250만원 이하라 낼 세금이 없어요", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/SettingsDetail.swift:545
            }
        }
        // ios: Views/SettingsDetail.swift:549
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T("올해 해외주식 실현 이익 (만원)", style = appFont(13f, FontWeight.SemiBold), color = Theme.sub)   // ios: Views/SettingsDetail.swift:550
            // ios: Views/SettingsDetail.swift:551,552,553,554,555,556
            InputField(gain, { v -> gain = v; m.taxGain = max(0.0, v.toDoubleOrNull() ?: 0.0) }, "",
                Modifier.background(Theme.card, RoundedCornerShape(10.dp)).border(2.dp, Theme.border, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp)
                    .semantics { contentDescription = tr("올해 해외주식 실현 이익 (만원)") },
                number = true, style = appFont(18f, FontWeight.SemiBold))
        }
        // ios: Views/SettingsDetail.swift:558
        Card {
            // ios: Views/SettingsDetail.swift:559,560,561,562
            listOf("해외주식 양도소득세" to "한 해 손익 합계에서 250만원 넘는 부분에 22%. 다음 해 5월 신고.",
                   "국내 상장주식" to "대주주가 아니면 양도세 없음. 증권거래세만.",
                   "배당" to "국내 15.4%, 미국 15% (현지 원천징수).",
                   "손실 상계" to "같은 해 이익과 손실을 합쳐서 계산해요.").forEach { (t, v) ->
                // ios: Views/SettingsDetail.swift:563,567
                Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(t, style = appFont(14f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:564
                    T(v, style = appFont(13f).ls(2f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:565
                }
            }
        }
        Note("계산 예시이며 세무 상담이 아닙니다.")   // ios: Views/SettingsDetail.swift:570
    }
}

// MARK: 사용 방법 (인터미션 뒤 한 번 보인 안내를 보관)

// ios: Views/SettingsDetail.swift:578,579
@Composable
fun HowToView() {
    // ios: Views/SettingsDetail.swift:580
    SettingsPage(title = "사용 방법") {
        // ios: Views/SettingsDetail.swift:581,582,583,584,585,586
        listOf(Triple("위젯", "위젯", "아침에 위젯 속 친구가 알려 주는 본전까지 남은 % 보기"),
               Triple("앱", "오늘의 1분", "오늘 숫자 하나와 질문 하나. 할 때마다 쉼터에 물건이 하나씩 돌아와요."),
               Triple("홈", "리마인드", "달성·새 소식은 친구 말풍선으로, 3일 안의 일정은 노란 띠로 떠요. 지난 소식은 ‘새 소식’에 30일 남아요."),
               Triple("알림", "알림", "비중이 계획에서 5%p 넘게 벗어난 날만 울려요."),
               Triple("미션", "1000칸", "본전을 1000칸으로 나눠 채워요."),
               Triple("미션", "주간 예보", "월요일에 금요일 범위를 적고, 금요일 종가로 도장을 찍어요.")).forEach { (w, t, v) ->
            // ios: Views/SettingsDetail.swift:587,596,597,598
            Row(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(14.dp)).border(1.dp, Theme.border, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // ios: Views/SettingsDetail.swift:588,589
                T(w, Modifier.background(Theme.mint, CapsuleShape).padding(horizontal = 8.dp, vertical = 3.dp), style = appFont(11f, FontWeight.Bold), color = Theme.inkFixed)
                // ios: Views/SettingsDetail.swift:590,594
                Column(Modifier.weight(1f).padding(end = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(t, style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:591
                    T(v, style = appFont(14f).ls(2f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:592
                }
            }
        }
    }
}

// MARK: 루트 바꾸기

// ios: Views/SettingsDetail.swift:606,607,610
@Composable
fun RouteView(m: AppModel) {
    var pick by remember { mutableStateOf("") }   // ios: Views/SettingsDetail.swift:608
    // ios: Views/SettingsDetail.swift:611
    val cards = listOf(Triple("minus", "회복 루트", "마이너스") to "본전까지 · 원인 진단 · 계획 4안 · 절세 · 4주 인터미션",
                       Triple("plus", "목표 루트 · 플러스", "플러스") to "지금 → 목표 금액 · 구성 비교 · 비중 세금 · 3개월 인터미션",   // ios: Views/SettingsDetail.swift:612
                       Triple("none", "목표 루트 · 시작 전", "시작 전") to "매달 적립으로 첫 목표 · 구성 고르기 · 3개월 인터미션")   // ios: Views/SettingsDetail.swift:613
    val cur = if (m.route == Route.recover) "minus" else if (m.route == Route.plus) "plus" else "none"   // ios: Views/SettingsDetail.swift:614
    val sel = if (pick.isEmpty()) cur else pick   // ios: Views/SettingsDetail.swift:615
    // ios: Views/SettingsDetail.swift:616
    SettingsPage(title = "루트 바꾸기") {
        FriendSay(m, key = "route", fallback = "길을 고르면 아래 그래프가 바뀌어요.")   // ios: Views/SettingsDetail.swift:617
        // ios: Views/SettingsDetail.swift:618
        if (m.route == Route.recover && m.total >= m.cost) {
            // ios: Views/SettingsDetail.swift:619,620,621
            T("본전 넘음 · 목표 루트 · 플러스로 바꿀 수 있어요", Modifier.fillMaxWidth().background(hexD(0xE3F4EC, 0x163226), RoundedCornerShape(12.dp)).padding(12.dp),
              style = appFont(14f, FontWeight.SemiBold), color = hexD(0x0B5E40, 0x7FD8B0))
        }
        // ios: Views/SettingsDetail.swift:623
        cards.forEach { (head, sub) ->
            val (k, name, tag) = head
            val on = sel == k   // ios: Views/SettingsDetail.swift:624
            // ios: Views/SettingsDetail.swift:625,626,634,635,636,638
            Column(
                Modifier.fillMaxWidth().plainClickable { pick = k }
                    .background(if (on) Theme.mintBg else Theme.card, RoundedCornerShape(16.dp)).border(2.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ios: Views/SettingsDetail.swift:627,629
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T(name, Modifier.weight(1f), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsDetail.swift:628
                    T(if (k == cur) "지금 루트" else tag, style = appFont(12f, FontWeight.Bold), color = if (k == cur) Theme.teal else Theme.sub)   // ios: Views/SettingsDetail.swift:630
                }
                T(sub, style = appFont(14f), color = Theme.sub)   // ios: Views/SettingsDetail.swift:632
            }
        }
        // ios: Views/SettingsDetail.swift:640
        PrimaryButton(title = if (sel == cur) "지금 루트" else "이 루트로", color = if (sel == cur) Theme.muted else Theme.teal) {
            if (sel == cur) return@PrimaryButton   // ios: Views/SettingsDetail.swift:641
            m.switchRoute(if (sel == "minus") Route.recover else if (sel == "plus") Route.plus else Route.novice)   // ios: Views/SettingsDetail.swift:642
            pick = ""; m.settingsPath = emptyList()   // ios: Views/SettingsDetail.swift:643
        }
        Note("바꿔도 받은 것은 그대로")   // ios: Views/SettingsDetail.swift:645
        // 첫 질문 화면처럼: 고른 루트에 따라 바뀌는 예시 3년 그래프
        StartForecastCard(route = if (sel == "minus") Route.recover else if (sel == "plus") Route.plus else Route.novice)   // ios: Views/SettingsDetail.swift:647
    }
}

/** 거주 국가 고르기: 세금 기능만 이 값을 따른다 (증권사 연결은 나라와 상관없이 누구나) */
// ios: Views/SettingsDetail.swift:653,654,655,662
@Composable
fun ResidenceView(m: AppModel) {
    var q by remember { mutableStateOf("") }   // ios: Views/SettingsDetail.swift:656
    // ios: Views/SettingsDetail.swift:657,658,659,660
    val all = remember {
        Locale.getISOCountries().map { it to Locale("", it).getDisplayCountry(Locale.getDefault()).ifEmpty { it } }
            .sortedWith(Comparator<Pair<String, String>> { a, b -> java.text.Collator.getInstance(Locale.getDefault()).compare(a.second, b.second) })
    }
    // 검색 전엔 지금 고른 나라를 맨 위에
    // ios: Views/SettingsDetail.swift:664
    val rows = if (q.isEmpty()) all.filter { it.first == m.residence } + all.filter { it.first != m.residence } else all.filter { it.second.contains(q, ignoreCase = true) || it.first.contains(q, ignoreCase = true) }
    // ios: Views/SettingsDetail.swift:677 navigationTitle: 이동 막대 제목과 같은 화면 이름
    Column(Modifier.fillMaxSize().background(Theme.bg).semantics { paneTitle = tr("거주 국가") }) {
        // ios: Views/SettingsDetail.swift:676 (검색 칸: 이동 막대 아래 늘 보임)
        InputField(q, { q = it }, "검색", Modifier.padding(horizontal = 16.dp, vertical = 8.dp).background(Theme.track, RoundedCornerShape(10.dp)).defaultMinSize(minHeight = 36.dp).padding(horizontal = 10.dp))
        // ios: Views/SettingsDetail.swift:665
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(rows, key = { it.first }) { r ->
                // ios: Views/SettingsDetail.swift:666,667,668,669
                Row(Modifier.fillMaxWidth().background(Theme.card).plainClickable { m.residence = r.first; m.settingsPath = m.settingsPath.dropLast(1) }.defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    T(r.second, Modifier.weight(1f), style = appFont(17f), color = Theme.ink)   // ios: Views/SettingsDetail.swift:670,671
                    if (r.first == m.residence) Sym("checkmark", 17f, Theme.teal)   // ios: Views/SettingsDetail.swift:672
                }
                HDivider(Theme.line)
            }
        }
    }
}

