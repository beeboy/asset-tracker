package com.naeilo.app.ui.settings

import com.naeilo.app.core.cformat
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.AlpacaFeed
import com.naeilo.app.core.AlpacaKeys
import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.KeyVault
import com.naeilo.app.core.LongbridgeFeed
import com.naeilo.app.core.LongbridgeKeys
import com.naeilo.app.core.NJson
import com.naeilo.app.core.TossFeed
import com.naeilo.app.core.TossKeys
import com.naeilo.app.core.calc.goalToText
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.core.calc.Route
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Shelter
import com.naeilo.app.net.Market
import com.naeilo.app.net.Sync
import com.naeilo.app.ui.goal.GoalSetView
import com.naeilo.app.data.Support
import com.naeilo.app.ui.support.SupportView
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.ChipRow
import com.naeilo.app.ui.ConfirmSheet
import com.naeilo.app.ui.DialogAction
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.InputField
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.Refreshable
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.SmoothImage
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.AppHeader
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.outset
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.GuidePickRow
import com.naeilo.app.ui.WaveFeedbackToggles
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

// 개발자: 출시 알림 신청 목록 (워커 GET /notify/list). 관리자 키는 이 기기 보관함(KeyVault)에, 비어 있으면 개발자 동기화 토큰을 쓴다.
// ios: Views/SettingsView.swift:4,16
@Composable
fun NotifyListView() {
    // ios: Views/SettingsView.swift:8
    var key by remember { mutableStateOf(AdminKey.load() ?: "") }
    // ios: Views/SettingsView.swift:9
    var rows by remember { mutableStateOf(listOf<NotifyList.Row>()) }
    // ios: Views/SettingsView.swift:10
    var busy by remember { mutableStateOf(false) }
    // ios: Views/SettingsView.swift:11
    var msg by remember { mutableStateOf<String?>(null) }
    // ios: Views/SettingsView.swift:12
    var copied by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val token = if (key.isEmpty()) (Sync.shared.creds?.token ?: "") else key   // ios: Views/SettingsView.swift:14

    // ios: Views/SettingsView.swift:55
    suspend fun load() {
        if (token.isEmpty()) { msg = "관리자 키를 넣어 주세요"; return }   // ios: Views/SettingsView.swift:56
        busy = true; msg = null; copied = false   // ios: Views/SettingsView.swift:57
        try {   // ios: Views/SettingsView.swift:58 (defer → finally)
            val req = FeedReq("https://asset-ai.drinker.workers.dev/notify/list", timeout = 20.0)   // ios: Views/SettingsView.swift:59,61
            req.setValue("Bearer $token", "Authorization")   // ios: Views/SettingsView.swift:60
            // ios: Views/SettingsView.swift:62
            try {
                val (c0, d) = req.send(); val code = maxOf(c0, 0)   // ios: Views/SettingsView.swift:63,65
                val r = runCatching { NJson.decodeFromString(NotifyList.Resp.serializer(), d.decodeToString()) }.getOrNull()   // ios: Views/SettingsView.swift:64
                if (code == 403) { msg = "키가 맞지 않아요"; return }   // ios: Views/SettingsView.swift:66
                val list = r?.rows
                if (code != 200 || list == null) { msg = r?.error ?: "목록을 못 받았어요 (${code})"; return }   // ios: Views/SettingsView.swift:67
                if (key.isNotEmpty()) AdminKey.save(key)   // ios: Views/SettingsView.swift:68
                rows = list.sortedByDescending { it.at }   // ios: Views/SettingsView.swift:69
                if (rows.isEmpty()) msg = "아직 신청한 사람이 없어요"   // ios: Views/SettingsView.swift:70
            } catch (e: Exception) {   // ios: Views/SettingsView.swift:71
                msg = "인터넷 연결을 확인해 주세요"   // ios: Views/SettingsView.swift:72
            }
        } finally { busy = false }
    }

    // ios: Views/SettingsView.swift:52
    LaunchedEffect(Unit) { if (token.isNotEmpty()) load() }
    // ios: Views/SettingsView.swift:17,48,50,51,18
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("출시 알림 신청", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:19
        // ios: Views/SettingsView.swift:20
        Card {
            // ios: Views/SettingsView.swift:21,22,23,24
            InputField(key, { key = it }, "관리자 키", Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true, onSubmit = { scope.launch { load() } })
            PrimaryButton(title = if (busy) "받는 중…" else "새로고침") { scope.launch { load() } }   // ios: Views/SettingsView.swift:25
            msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:26
        }
        // ios: Views/SettingsView.swift:28
        if (rows.isNotEmpty()) {
            // ios: Views/SettingsView.swift:29
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("${rows.size}명", Modifier.weight(1f), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsView.swift:30,31
                // ios: Views/SettingsView.swift:32,33,34
                T(if (copied) "복사됨" else "주소 전부 복사", Modifier.plainClickable { clip.setText(AnnotatedString(rows.joinToString(", ") { it.email })); copied = true },
                  style = appFont(15f, FontWeight.SemiBold), color = Theme.teal)
            }
            // ios: Views/SettingsView.swift:36
            Card {
                // ios: Views/SettingsView.swift:37
                rows.forEachIndexed { i, r ->
                    if (i > 0) HDivider(Theme.line)   // ios: Views/SettingsView.swift:38
                    // ios: Views/SettingsView.swift:39,41
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        T(r.email, Modifier.weight(1f), style = appFont(14f), maxLines = 1)   // ios: Views/SettingsView.swift:40
                        T(r.at, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:42
                    }
                }
            }
        }
    }
}
object NotifyList {
    // ios: Views/SettingsView.swift:5
    @Serializable data class Row(val email: String, val at: String)
    // ios: Views/SettingsView.swift:6
    @Serializable data class Resp(val n: Int? = null, val rows: List<Row>? = null, val error: String? = null)
}

/** 출시 알림 목록용 관리자 키 (이 기기 보관함에만) */
// ios: Views/SettingsView.swift:78
object AdminKey {
    private val q = KeyVault.slot("com.naeilo.admin", "notify")   // ios: Views/SettingsView.swift:79
    // ios: Views/SettingsView.swift:80,81,82,83,85 (84 접근성은 KeyVault 키가 원래 이 기기 전용)
    fun save(k: String) { KeyVault.delete(q); KeyVault.add(q, k.toByteArray(Charsets.UTF_8)) }
    // ios: Views/SettingsView.swift:87,88,89,90,91,92,93
    fun load(): String? = KeyVault.read(q)?.toString(Charsets.UTF_8)
}

// ios: Views/SettingsView.swift:97
enum class SettingsRoute { alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence }

/** 설정 한 줄 (제목, 설명, 갈 곳) */
private class SRow(val title: String, val sub: String, val to: SettingsRoute)

// ios: Views/SettingsView.swift:99,100,102
@Composable
fun SettingsView(m: AppModel) {
    val ctx = LocalContext.current
    val onN = m.alerts.values.count { it }   // ios: Views/SettingsView.swift:103
    // 위에서 아래로: 오픈 API 카드 → 내 목표 → 알림과 위젯 → 함께할 인물 → 화면 → 계산 기준 → 도움과 기타
    val routeLine = if (m.route == Route.recover) "본전부터 되찾는 중이에요" else if (m.route == Route.plus) "목표를 향해 가는 중이에요" else "아직 첫 단계예요"   // ios: Views/SettingsView.swift:105
    // ios: Views/SettingsView.swift:106
    val goalRows = listOf(
        SRow("목표 고치기", if (m.isGoal) "${m.goalToText}까지 ${AppModel.wonK(m.gK)}" else "아직 목표가 없어요", SettingsRoute.goal),   // ios: Views/SettingsView.swift:107
        SRow("루트 바꾸기", routeLine, SettingsRoute.route),   // ios: Views/SettingsView.swift:108
    )
    // ios: Views/SettingsView.swift:110
    val oftenRows = listOf(
        SRow("알림", if (onN > 0) "${onN}개 켜짐" else "모두 꺼짐", SettingsRoute.alerts),   // ios: Views/SettingsView.swift:111
        SRow("홈 화면 위젯", "${m.baseWidgets.size + Shelter.friends.size}가지 · 미리보기", SettingsRoute.widgets),   // ios: Views/SettingsView.swift:112
    )
    // 증권사는 나라와 상관없이 누구나 연결: 거주 국가에 맞는 것만 위로 올린다
    val kr = m.residence == "KR"   // ios: Views/SettingsView.swift:115
    val lbFirst = listOf("HK", "CN", "SG", "MO", "TW", "MY").contains(m.residence)   // ios: Views/SettingsView.swift:116
    // ios: Views/SettingsView.swift:117
    val brokerRows = listOf(
        SRow("내 Alpaca 키", if (AlpacaKeys.on) "미국 종목을 Alpaca(IEX) 시세로 받는 중" else "넣지 않음 · 미국 종목 실시간 시세", SettingsRoute.alpaca),   // ios: Views/SettingsView.swift:118
        SRow("내 Longbridge 키", if (LongbridgeKeys.on) "미국 종목을 Longbridge 시세로 받는 중" else "넣지 않음 · 미국·홍콩·중국 실시간 시세", SettingsRoute.longbridge),   // ios: Views/SettingsView.swift:119
    )
    // ios: Views/SettingsView.swift:121
    val refRows = listOf(
        SRow("시세 기준", "종목·환율 시세를 어디서 받는지", SettingsRoute.price),   // ios: Views/SettingsView.swift:122
        SRow("거주 국가", "${m.residenceName} · 세금 계산만 이 나라를 따라요", SettingsRoute.residence),   // ios: Views/SettingsView.swift:123
        SRow("세금", if (kr) "한국 거주자 기준으로 계산해요" else "이 나라 규칙은 준비 중 · 지금은 한국 기준", SettingsRoute.tax),   // ios: Views/SettingsView.swift:124
    ) + (if (lbFirst) brokerRows.reversed() else brokerRows)   // ios: Views/SettingsView.swift:125
    // 토스 키 줄은 맨 위 오픈 API 카드로 옮겼다 (동기화 코드는 남김)
    // ios: Views/SettingsView.swift:127
    val helpRows = listOf(
        SRow("처음 쓰는 법", "매일 1분 · 목표 칸 · 주간 예보", SettingsRoute.howto),   // ios: Views/SettingsView.swift:128
        // ios: Views/SettingsView.swift:129
        SRow("구독", Support.shared.plan?.let { "${it.nameText} 중 · 고마워요" } ?: (if (Support.shared.inTrial) "7일 무료 · ${Support.shared.trialLeft}일 남음" else "월 1,000원 · 연 9,000원"), SettingsRoute.support),
    )
    // ios: Views/SettingsView.swift:131,176,178,179
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen {
            // ios: Views/SettingsView.swift:132
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.outset(16.dp)) { AppHeader(tab = "설정") }   // ios: Views/SettingsView.swift:133 (-16)
                if (kr) ApiCard(m)   // ios: Views/SettingsView.swift:134
                Group(m, "내 목표", goalRows)   // ios: Views/SettingsView.swift:135
                Group(m, "알림과 위젯", oftenRows)   // ios: Views/SettingsView.swift:136
                Head("함께할 인물")   // ios: Views/SettingsView.swift:137
                GuidePickRow(m)   // ios: Views/SettingsView.swift:138
                Head("화면")   // ios: Views/SettingsView.swift:139
                T("밝기 모드", style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/SettingsView.swift:140
                // ios: Views/SettingsView.swift:141,142
                ChipRow(items = listOf("system" to "기기 설정대로", "light" to "밝게", "dark" to "어둡게"),
                        selection = m.appearance, onSelect = { m.appearance = it; Defaults.prefs.edit().putString("appearance", it).apply() }, fill = true)
                Group(m, null, listOf(SRow("앱 아이콘", AppIconPicker.currentName(ctx), SettingsRoute.icon)))   // ios: Views/SettingsView.swift:143
                // 앱 언어는 안드로이드 설정의 앱 언어 화면에서 바꾼다 (누르면 그 화면이 열린다)
                // ios: Views/SettingsView.swift:145,146,147,153,155,156,157
                Row(
                    Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp))
                        .plainClickable { openAppLanguage(ctx) }.defaultMinSize(minHeight = 52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,   // ios: Views/SettingsView.swift:148,150
                ) {
                    T("언어 변경", Modifier.weight(1f), style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:149
                    Sym("arrow.up.right", 13f, Theme.muted)   // ios: Views/SettingsView.swift:151
                }
                WaveFeedbackToggles()   // ios: Views/SettingsView.swift:158
                Group(m, "숫자를 계산하는 기준", refRows)   // ios: Views/SettingsView.swift:159
                if (!kr) ApiCard(m)   // ios: Views/SettingsView.swift:160
                Group(m, "도움과 기타", helpRows)   // ios: Views/SettingsView.swift:161
                // 시연 상태로 되돌리기는 내 종목을 샘플로 덮어쓴다: 개발자 기기(테스트 빌드)에서만
                // ios: Views/SettingsView.swift:163
                if (m.devAll) {
                    T("시연 상태 (개발자)", Modifier.padding(top = 8.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/SettingsView.swift:164
                    // ios: Views/SettingsView.swift:165
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DemoButton("첫 질문부터") { m.onboarded = false; Defaults.prefs.edit().putBoolean("onboarded", false).apply() }   // ios: Views/SettingsView.swift:166
                        DemoButton("미션 1부터") { m.resetDemo(AppModel.Demo.fresh) }   // ios: Views/SettingsView.swift:167
                        DemoButton("인터미션 1주차로") { m.resetDemo(AppModel.Demo.week1) }   // ios: Views/SettingsView.swift:168
                        DemoButton("모든 화면 열기") { m.resetDemo(AppModel.Demo.all) }   // ios: Views/SettingsView.swift:169
                    }
                    Group(m, null, listOf(SRow("출시 알림 신청 목록", "naeilo.com 앱 받기 신청", SettingsRoute.notifyList)))   // ios: Views/SettingsView.swift:171
                }
                T("시세: ${Market.shared.source} · ${Config.channel.raw}", style = appFont(12f), color = Theme.muted)   // ios: Views/SettingsView.swift:173
                T("버전 0.1 · 서재: 외전 프롤로그~5장 (한글·영문)", style = appFont(12f), color = Theme.muted)   // ios: Views/SettingsView.swift:174
            }
        }
    }
}

/** 설정에서 여는 화면 (navigationDestination for: SettingsRoute) */
// ios: Views/SettingsView.swift:180,181
fun settingsPage(m: AppModel, r: SettingsRoute): NavPage = when (r) {
    SettingsRoute.alerts -> NavPage { AlertsView(m) }   // ios: Views/SettingsView.swift:182
    SettingsRoute.sync -> NavPage { SyncView(m) }   // ios: Views/SettingsView.swift:183
    SettingsRoute.widgets -> NavPage { WidgetPickView(m) }   // ios: Views/SettingsView.swift:184
    SettingsRoute.tax -> NavPage { TaxRulesView(m) }   // ios: Views/SettingsView.swift:185
    SettingsRoute.price -> NavPage { PriceBasisView(m) }   // ios: Views/SettingsView.swift:186
    SettingsRoute.toss -> NavPage { TossKeyView(m) }   // ios: Views/SettingsView.swift:187
    SettingsRoute.alpaca -> NavPage { AlpacaKeyView(m) }   // ios: Views/SettingsView.swift:188
    SettingsRoute.longbridge -> NavPage { LongbridgeKeyView(m) }   // ios: Views/SettingsView.swift:189
    SettingsRoute.howto -> NavPage { HowToView() }   // ios: Views/SettingsView.swift:190
    SettingsRoute.route -> NavPage { RouteView(m) }   // ios: Views/SettingsView.swift:191
    SettingsRoute.goal -> NavPage { GoalSetView(m, fromSettings = true) }   // ios: Views/SettingsView.swift:192
    SettingsRoute.support -> NavPage { SupportView(m) }   // ios: Views/SettingsView.swift:193
    SettingsRoute.icon -> NavPage { AppIconScreen(m) }   // ios: Views/SettingsView.swift:194
    SettingsRoute.notifyList -> NavPage { NotifyListView() }   // ios: Views/SettingsView.swift:195
    SettingsRoute.residence -> NavPage("거주 국가") { ResidenceView(m) }   // ios: Views/SettingsView.swift:196 (제목은 ResidenceView 의 navigationTitle)
    SettingsRoute.charPreview -> NavPage { WidgetPickView(m) }     // ios: Views/SettingsView.swift:197 예전 '인물 위젯 미리보기'는 위젯 화면으로 합쳤다
}

/** 앱 언어 화면 (안드로이드 13+: 앱별 언어, 그 전: 앱 정보) */
private fun openAppLanguage(ctx: Context) {
    val pkg = Uri.fromParts("package", ctx.packageName, null)
    val i = if (Build.VERSION.SDK_INT >= 33) Intent(Settings.ACTION_APP_LOCALE_SETTINGS, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** 맨 위 오픈 API 카드: 연결 상태 한 줄 + 내 폰·서버·토스 작은 띠. 누르면 토스증권 오픈 API 화면으로 */
// ios: Views/SettingsView.swift:203
@Composable
private fun ApiCard(m: AppModel) {
    Market.shared.tossDone      // ios: Views/SettingsView.swift:204 토스 받기를 마치면 다시 그린다
    val on = TossKeys.on   // ios: Views/SettingsView.swift:205
    // ios: Views/SettingsView.swift:206,207,231,232,233,235
    Fg(Color.White) {
        Column(
            Modifier.fillMaxWidth().plainClickable { m.settingsPath = m.settingsPath + SettingsRoute.toss }
                .background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ios: Views/SettingsView.swift:208
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (on) "내 오픈 API 키로 시세 받는 중" else "내 오픈 API 키 연결 안 됨", style = appFont(15f, FontWeight.SemiBold))   // ios: Views/SettingsView.swift:209
                if (on) Sym("chevron.right", 12f, Color.White)   // ios: Views/SettingsView.swift:210
            }
            // ios: Views/SettingsView.swift:212,223
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniNode("iphone", on); MiniLine(on)   // ios: Views/SettingsView.swift:213
                MiniNode("server.rack", on); MiniLine(on)   // ios: Views/SettingsView.swift:214
                MiniNode("chart.line.uptrend.xyaxis", on)   // ios: Views/SettingsView.swift:215
                // ios: Views/SettingsView.swift:216,220,221
                Column(Modifier.weight(1f).padding(start = 5.dp, end = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(if (on) "이어져 있어요" else "키는 이 폰에만 있어요", style = appFont(13f, FontWeight.Bold))   // ios: Views/SettingsView.swift:217
                    // ios: Views/SettingsView.swift:218,219
                    T(if (on) "현재가·일봉·환율만 받아 왔어요" else "서버는 지나가기만 해요 · 현재가·일봉·환율만", style = appFont(11f), color = hex(0xC9D0D6))
                }
            }
            // ios: Views/SettingsView.swift:224
            if (!on) {
                // 다른 버튼(PrimaryButton)과 같은 모양: 청록 바탕 + 흰 글자 (라이트·다크 공통)
                // ios: Views/SettingsView.swift:226,227,228
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp).background(Theme.teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    T("연결하기", style = appFont(16f, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

// ios: Views/SettingsView.swift:238,239,240,241,242
@Composable
private fun MiniNode(icon: String, on: Boolean) {
    Box(Modifier.size(30.dp, 30.dp).background(if (on) Theme.teal else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).semantics { invisibleToUser() }, contentAlignment = Alignment.Center) {
        Sym(icon, 13f, if (on) Color.White else hex(0xC9D0D6), scaled = false)   // .font(.system(size: 13, weight: .semibold))
    }
}

/** 작은 띠의 줄: 끊김은 회색 점선, 이어짐은 청록 실선 */
// ios: Views/SettingsView.swift:246,247,248,249,250
@Composable
private fun MiniLine(on: Boolean) {
    val c = if (on) Theme.teal else Theme.muted
    Canvas(Modifier.size(12.dp, 2.dp).semantics { invisibleToUser() }) {
        drawLine(c, Offset(0f, 1.dp.toPx()), Offset(12.dp.toPx(), 1.dp.toPx()), 2.dp.toPx(),
            pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
    }
}

// ios: Views/SettingsView.swift:253,254
@Composable
private fun Head(t: String) = T(t, Modifier.padding(top = 8.dp), style = appFont(13f, FontWeight.Bold), color = Theme.teal)

// ios: Views/SettingsView.swift:257
@Composable
private fun Group(m: AppModel, title: String?, rows: List<SRow>) {
    if (title != null) Head(title)   // ios: Views/SettingsView.swift:258
    // ios: Views/SettingsView.swift:259,275,276
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ios: Views/SettingsView.swift:260
        rows.forEachIndexed { i, r ->
            if (i > 0) HDivider(Theme.line)   // ios: Views/SettingsView.swift:261
            // ios: Views/SettingsView.swift:262,263,271,272
            Row(Modifier.fillMaxWidth().plainClickable { m.settingsPath = m.settingsPath + r.to }.defaultMinSize(minHeight = 60.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // ios: Views/SettingsView.swift:264,268
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(r.title, style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:265
                    T(r.sub, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:266
                }
                Sym("chevron.right", 13f, Theme.muted)   // ios: Views/SettingsView.swift:269
            }
        }
    }
}

// ios: Views/SettingsView.swift:279,280,284
@Composable
private fun androidx.compose.foundation.layout.RowScope.DemoButton(t: String, a: () -> Unit) {
    val muted = Theme.muted
    // ios: Views/SettingsView.swift:281,282,283
    Box(
        Modifier.weight(1f).plainClickable(onClick = a).defaultMinSize(minHeight = 44.dp)
            .drawDashedRound(muted, 10f, 1f, floatArrayOf(4f, 3f)),
        contentAlignment = Alignment.Center,
    ) { T(t, style = appFont(14f, FontWeight.SemiBold), color = Theme.ink, maxLines = 1) }
}

/** 점선 둥근 테두리 (RoundedRectangle().stroke(style: StrokeStyle(lineWidth:, dash:))) */
internal fun Modifier.drawDashedRound(c: Color, radius: Float, width: Float, dash: FloatArray): Modifier = this.then(
    Modifier.drawBehind {
        drawRoundRect(c, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * density),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width * density, pathEffect = PathEffect.dashPathEffect(dash.map { it * density }.toFloatArray())))
    })

// ios: Views/SettingsView.swift:288,289,290
@Composable
fun PriceBasisView(m: AppModel) {
    val mk = Market.shared; val fx = mk.fx   // ios: Views/SettingsView.swift:291
    // ios: Views/SettingsView.swift:292,317,318,319
    Refreshable({ Market.shared.pullRefresh(m) }, Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/SettingsView.swift:293,315
            T("시세 기준", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:294
            // ios: Views/SettingsView.swift:295
            val main = if (mk.toss != null) "토스 오픈 API" else if (mk.alpaca != null) "Alpaca 시세" else if (mk.longbridge != null) "Longbridge 시세" else if (mk.site != null) "naeilo 사이트 시세" else "예시 값"
            // ios: Views/SettingsView.swift:296,301,302
            Fg(Color.White) {
                Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("지금 보는 숫자", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/SettingsView.swift:297
                    T(main, style = appFont(28f, FontWeight.Bold))   // ios: Views/SettingsView.swift:298
                    T(mk.asOfText.replace(" (테스트 자료)", ""), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/SettingsView.swift:299
                }
            }
            // ios: Views/SettingsView.swift:303
            Card {
                PRow("받는 순서", "내 토스 키 → 내 Alpaca·Longbridge 키 → naeilo 사이트. 앞에서 받은 값을 먼저 써요")   // ios: Views/SettingsView.swift:304
                PRow("미국 종목", if (mk.toss != null) "토스 지금 가격" else if (mk.alpaca != null) "Alpaca(IEX) 지금 가격" else if (mk.longbridge != null) "Longbridge 지금 가격" else "사이트가 평일 30분마다 모은 가격")   // ios: Views/SettingsView.swift:305
                PRow("한국 종목", if (mk.toss != null) "토스 지금 가격" else "사이트가 모은 가격. 미국 지수를 따르는 국내 ETF는 그 미국 ETF × 원/달러로 추정해요")   // ios: Views/SettingsView.swift:306
                PRow("장 마감 뒤", "미국 장이 끝나면 정규장 종가로 세요 (토스 총자산과 같은 기준)")   // ios: Views/SettingsView.swift:307
                // Views/SettingsView.swift:308 (검사 예외: 같은 줄에 옮김)
                PRow("원/달러", "${cformat("%,d", fx.last.roundToLong())}원 · 오늘 ${AppModel.sgn(fx.change)}")
                // Views/SettingsView.swift:309 (검사 예외: 같은 줄에 옮김)
                PRow("평가 환율", if (mk.marRate != null) "매매기준율 ${cformat("%.2f", mk.valFx)}원 · 웹 평가액과 같아요" else "매매기준율을 아직 못 받아 지금 환율")
                PRow("오늘의 움직임", "전일 종가 대비 지금 평가액")   // ios: Views/SettingsView.swift:310
            }
            // ios: Views/SettingsView.swift:312,313
            T("그래프와 미션은 종가로 계산해요. 아래로 당기면 바로 다시 받아요.", style = appFont(13f).ls(2f), color = Theme.sub)
        }
    }
}
// ios: Views/SettingsView.swift:321,322,323
@Composable
private fun PRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) { T(k, Modifier.width(80.dp), style = appFont(14f, FontWeight.SemiBold)); T(v, Modifier.weight(1f), style = appFont(14f), color = Theme.sub) }
}

/** "마지막으로 받은 때 …" 의 때: 오늘이면 시각만 (a h:mm:ss), 아니면 날짜도 */
internal fun okAtText(t: Double, todayFmt: String, otherFmt: String): String {
    val d = Instant.ofEpochMilli((t * 1000).toLong())
    val z = ZoneId.systemDefault()
    val today = LocalDate.now(z) == d.atZone(z).toLocalDate()
    return DateTimeFormatter.ofPattern(if (today) todayFmt else otherFmt, AppModel.dateLocale).withZone(z).format(d)
}

// 토스증권 시세 (일반 사용자): 내가 받은 토스 Open API 키를 넣으면 국내·미국 종목 지금 가격을 토스에서 받는다.
// 키는 이 폰 보관함에만 두고, 공용 중계(Config.tossRelay)는 키를 저장하지 않고 토스에 넘기기만 한다.
// 토스는 등록한 IP 에서만 받으므로 사용자는 중계 IP 를 토스 WTS 허용 IP 에 넣어야 한다.
// ios: Views/SettingsView.swift:330,331,355
@Composable
fun TossKeyView(m: AppModel) {
    var id by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:332
    var secret by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:333
    var ip by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:334
    var busy by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:335
    var msg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:336
    var on by remember { mutableStateOf(TossKeys.on) }   // ios: Views/SettingsView.swift:337
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:338
    var copied by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:339
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current

    // ios: Views/SettingsView.swift:341
    val status: String = run {
        Market.shared.tossDone      // ios: Views/SettingsView.swift:342 토스 받기를 마치면 다시 그린다
        val t = TossFeed.okAt   // ios: Views/SettingsView.swift:343
        var s: String   // ios: Views/SettingsView.swift:344
        // ios: Views/SettingsView.swift:345,346,347,348,349
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s = "마지막으로 받은 때 $f"
        } else { s = "아직 받은 적 없어요" }   // ios: Views/SettingsView.swift:350
        TossFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsView.swift:351
        s   // ios: Views/SettingsView.swift:352
    }

    /** 토큰을 새로 받아 키·허용 IP 를 확인하고, 되면 시세를 바로 받는다 */
    // ios: Views/SettingsView.swift:427
    suspend fun check() {
        busy = true; msg = null   // ios: Views/SettingsView.swift:428
        // 토큰은 만료 전이면 그대로 쓴다 (새로 받으면 받는 중인 다른 요청의 토큰이 끊긴다). 처음 연결이면 키를 넣을 때 토큰을 지워 새로 받는다
        val (t, e) = TossFeed.token()   // ios: Views/SettingsView.swift:430
        // ios: Views/SettingsView.swift:431
        if (t == null) {
            msg = (e ?: "연결 실패") + (if (e == "허용 IP에 중계 IP가 없어요") ". 오픈 API 허용 IP에 위 IP를 넣고 다시 눌러 주세요." else "")   // ios: Views/SettingsView.swift:432
            if (!on) TossKeys.clear()     // ios: Views/SettingsView.swift:433 처음 연결이 실패하면 키를 남기지 않는다
        } else {   // ios: Views/SettingsView.swift:434
            on = true   // ios: Views/SettingsView.swift:435
            Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Views/SettingsView.swift:436
        }
        busy = false   // ios: Views/SettingsView.swift:438
    }

    // ios: Views/SettingsView.swift:408
    @Composable
    fun IpRow(ip: String) {
        // ios: Views/SettingsView.swift:409,415
        Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(ip, Modifier.weight(1f), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsView.swift:410,411
            // ios: Views/SettingsView.swift:412,413
            Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable { clip.setText(AnnotatedString(ip)); copied = true }, contentAlignment = Alignment.Center) {
                T(if (copied) "복사됨" else "복사", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)
            }
        }
    }

    // ios: Views/SettingsView.swift:418,419,420,422,423
    @Composable
    fun Field(title: String, v: String, set: (String) -> Unit, secure: Boolean) {
        InputField(v, set, title, Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = secure)
    }

    // ios: Views/SettingsView.swift:366
    LaunchedEffect(Unit) { ip = TossKeys.relayIP() }
    // ios: Views/SettingsView.swift:356,362,364,365,357
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("오픈 API", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:358
        TossLinkMap(on = on)   // ios: Views/SettingsView.swift:359
        // ios: Views/SettingsView.swift:360,369,370
        if (on) Card {
            T("내 키로 받는 중", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:371
            T(status, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:372
            ip?.let { IpRow(it) }   // ios: Views/SettingsView.swift:373
            PrimaryButton(title = if (busy) "받는 중…" else "지금 다시 받기") { scope.launch { check() } }   // ios: Views/SettingsView.swift:374
            msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:375
            // ios: Views/SettingsView.swift:376,377
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).plainClickable { askOff = true }, contentAlignment = Alignment.Center) {
                T("키 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
            }
            // ios: Views/SettingsView.swift:378,379,380
            ConfirmSheet(askOff, "오픈 API 키를 이 폰에서 지울까요?", "기본 시세로 돌아가요. 증권사에서 받은 키는 그 증권사에서 따로 지울 수 있어요.",
                listOf(DialogAction("지우기", destructive = true, onClick = { TossKeys.clear(); on = false; msg = null; scope.launch { Market.shared.pullRefresh(m) } }))) { askOff = false }
        } else Card {   // ios: Views/SettingsView.swift:384,385
            // 안내는 세 줄만 (토스 키 발급 절차는 설명하지 않는다)
            // ios: Views/SettingsView.swift:387
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("info.circle.fill", 15f, Theme.teal, Modifier.padding(top = 2.dp))   // ios: Views/SettingsView.swift:388
                T("투자자가 본인의 API Key를 통해 본인 정보를 호출하는 구조예요.", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:389,391
            }
            T("1. 허용 IP에 서버 중계 IP를 넣어 주세요.", Modifier.padding(top = 6.dp), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/SettingsView.swift:392
            // ios: Views/SettingsView.swift:393
            ip?.let { IpRow(it) } ?: T("중계 IP를 불러오는 중이에요…", style = appFont(13f), color = Theme.muted)
            T("2. 클라이언트 키를 넣고 서버와 연결하세요.", Modifier.padding(top = 6.dp), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/SettingsView.swift:394
            Field("client_id", id, { id = it }, secure = true)   // ios: Views/SettingsView.swift:395
            Field("client_secret", secret, { secret = it }, secure = true)   // ios: Views/SettingsView.swift:396
            val ok = id.trim().isNotEmpty() && secret.trim().isNotEmpty()   // ios: Views/SettingsView.swift:397
            // ios: Views/SettingsView.swift:398
            PrimaryButton(title = if (busy) "확인 중…" else "연결하기", color = if (ok) Theme.teal else Theme.muted) {
                if (!ok || busy) return@PrimaryButton   // ios: Views/SettingsView.swift:399
                TossKeys.save(TossKeys.Keys(id = id.trim(), secret = secret.trim()))   // ios: Views/SettingsView.swift:400
                secret = ""   // ios: Views/SettingsView.swift:401
                scope.launch { check() }   // ios: Views/SettingsView.swift:402
            }
            msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:404
        }
    }
}

// 내 Alpaca 키 (미국 종목만): app.alpaca.markets 모의 계정에서 무료로 받은 키를 넣으면 미국 종목 지금 가격을 Alpaca(IEX)에서 받는다.
// 키는 이 폰 보관함에만 두고 Alpaca 를 직접 부른다 (중계 없음). 토스 키가 있으면 토스 값이 먼저다.
// ios: Views/SettingsView.swift:444,445,467
@Composable
fun AlpacaKeyView(m: AppModel) {
    var id by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:446
    var secret by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:447
    var busy by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:448
    var msg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:449
    var on by remember { mutableStateOf(AlpacaKeys.on) }   // ios: Views/SettingsView.swift:450
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:451
    val scope = rememberCoroutineScope()

    // ios: Views/SettingsView.swift:453
    val status: String = run {
        Market.shared.alpacaDone      // ios: Views/SettingsView.swift:454 Alpaca 받기를 마치면 다시 그린다
        val t = AlpacaFeed.okAt   // ios: Views/SettingsView.swift:455
        var s: String   // ios: Views/SettingsView.swift:456
        // ios: Views/SettingsView.swift:457,458,459,460,461
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s = "마지막으로 받은 때 $f"
        } else { s = "아직 받은 적 없어요" }   // ios: Views/SettingsView.swift:462
        AlpacaFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsView.swift:463
        s   // ios: Views/SettingsView.swift:464
    }

    /** 키를 확인하고, 되면 시세를 바로 받는다. 처음 연결이 실패하면 키를 남기지 않는다 */
    // ios: Views/SettingsView.swift:512
    suspend fun check() {
        busy = true; msg = null   // ios: Views/SettingsView.swift:513
        val e = AlpacaFeed.check()
        // ios: Views/SettingsView.swift:514
        if (e != null) {
            msg = e   // ios: Views/SettingsView.swift:515
            if (!on) AlpacaKeys.clear()   // ios: Views/SettingsView.swift:516
        } else {   // ios: Views/SettingsView.swift:517
            on = true   // ios: Views/SettingsView.swift:518
            Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Views/SettingsView.swift:519
        }
        busy = false   // ios: Views/SettingsView.swift:521
    }

    // ios: Views/SettingsView.swift:505,506,507,508
    @Composable
    fun Field(title: String, v: String, set: (String) -> Unit) {
        InputField(v, set, title, Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)
    }

    // ios: Views/SettingsView.swift:468,499,501,502,469
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("내 Alpaca 키", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:470
        // ios: Views/SettingsView.swift:471
        Card {
            // ios: Views/SettingsView.swift:472
            if (on) {
                T("내 키로 받는 중", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:473
                T(status, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:474
                PrimaryButton(title = if (busy) "받는 중…" else "지금 다시 받기") { scope.launch { check() } }   // ios: Views/SettingsView.swift:475
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:476
                // ios: Views/SettingsView.swift:477,478
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).plainClickable { askOff = true }, contentAlignment = Alignment.Center) {
                    T("키 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
                }
                // ios: Views/SettingsView.swift:479,480,481
                ConfirmSheet(askOff, "Alpaca 키를 이 폰에서 지울까요?", "기본 시세로 돌아가요.",
                    listOf(DialogAction("지우기", destructive = true, onClick = { AlpacaKeys.clear(); on = false; msg = null; scope.launch { Market.shared.pullRefresh(m) } }))) { askOff = false }
            } else {   // ios: Views/SettingsView.swift:482
                T("미국 종목만 Alpaca에서 지금 가격을 받아요. 키는 이 폰에만 둬요.", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:483
                // ios: Views/SettingsView.swift:484,485
                T("app.alpaca.markets 모의(Paper) 계정에서 무료로 받을 수 있어요. 무료 키는 IEX 거래소 실시간 시세예요.", style = appFont(13f), color = Theme.sub)
                Field("API Key ID", id) { id = it }   // ios: Views/SettingsView.swift:486
                Field("Secret Key", secret) { secret = it }   // ios: Views/SettingsView.swift:487
                val ok = id.trim().isNotEmpty() && secret.trim().isNotEmpty()   // ios: Views/SettingsView.swift:488
                // ios: Views/SettingsView.swift:489
                PrimaryButton(title = if (busy) "확인 중…" else "연결하기", color = if (ok) Theme.teal else Theme.muted) {
                    if (!ok || busy) return@PrimaryButton   // ios: Views/SettingsView.swift:490
                    AlpacaKeys.save(AlpacaKeys.Keys(id = id.trim(), secret = secret.trim()))   // ios: Views/SettingsView.swift:491
                    secret = ""   // ios: Views/SettingsView.swift:492
                    scope.launch { check() }   // ios: Views/SettingsView.swift:493
                }
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:495
            }
        }
    }
}

// 내 Longbridge 키: open.longbridge.com 개발자 센터에서 받은 App Key·App Secret·Access Token 을 넣으면 지금 가격을 Longbridge 에서 받는다.
// 앱은 아직 원화·달러 종목만 다루므로 지금은 미국 종목만. 키는 이 폰 보관함에만 두고 Longbridge 를 직접 부른다 (중계 없음). 토스·Alpaca 키가 있으면 그 값이 먼저다.
// ios: Views/SettingsView.swift:527,528,551
@Composable
fun LongbridgeKeyView(m: AppModel) {
    var key by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:529
    var secret by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:530
    var token by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:531
    var busy by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:532
    var msg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:533
    var on by remember { mutableStateOf(LongbridgeKeys.on) }   // ios: Views/SettingsView.swift:534
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:535
    val scope = rememberCoroutineScope()

    // ios: Views/SettingsView.swift:537
    val status: String = run {
        Market.shared.longbridgeDone      // ios: Views/SettingsView.swift:538 Longbridge 받기를 마치면 다시 그린다
        val t = LongbridgeFeed.okAt   // ios: Views/SettingsView.swift:539
        var s: String   // ios: Views/SettingsView.swift:540
        // ios: Views/SettingsView.swift:541,542,543,544,545
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s = "마지막으로 받은 때 $f"
        } else { s = "아직 받은 적 없어요" }   // ios: Views/SettingsView.swift:546
        LongbridgeFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsView.swift:547
        s   // ios: Views/SettingsView.swift:548
    }

    /** 키를 확인하고, 되면 시세를 바로 받는다. 처음 연결이 실패하면 키를 남기지 않는다 */
    // ios: Views/SettingsView.swift:598
    suspend fun check() {
        busy = true; msg = null   // ios: Views/SettingsView.swift:599
        val e = LongbridgeFeed.check()
        // ios: Views/SettingsView.swift:600
        if (e != null) {
            msg = e   // ios: Views/SettingsView.swift:601
            if (!on) LongbridgeKeys.clear()   // ios: Views/SettingsView.swift:602
        } else {   // ios: Views/SettingsView.swift:603
            on = true   // ios: Views/SettingsView.swift:604
            Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Views/SettingsView.swift:605
        }
        busy = false   // ios: Views/SettingsView.swift:607
    }

    // ios: Views/SettingsView.swift:591,592,593,594
    @Composable
    fun Field(title: String, v: String, set: (String) -> Unit) {
        InputField(v, set, title, Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)
    }

    // ios: Views/SettingsView.swift:552,585,587,588,553
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("내 Longbridge 키", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:554
        // ios: Views/SettingsView.swift:555
        Card {
            // ios: Views/SettingsView.swift:556
            if (on) {
                T("내 키로 받는 중", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:557
                T(status, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:558
                PrimaryButton(title = if (busy) "받는 중…" else "지금 다시 받기") { scope.launch { check() } }   // ios: Views/SettingsView.swift:559
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:560
                // ios: Views/SettingsView.swift:561,562
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).plainClickable { askOff = true }, contentAlignment = Alignment.Center) {
                    T("키 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
                }
                // ios: Views/SettingsView.swift:563,564,565
                ConfirmSheet(askOff, "Longbridge 키를 이 폰에서 지울까요?", "기본 시세로 돌아가요.",
                    listOf(DialogAction("지우기", destructive = true, onClick = { LongbridgeKeys.clear(); on = false; msg = null; scope.launch { Market.shared.pullRefresh(m) } }))) { askOff = false }
            } else {   // ios: Views/SettingsView.swift:566
                T("미국 종목 지금 가격을 Longbridge에서 받아요. 키는 이 폰에만 둬요.", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:567
                // ios: Views/SettingsView.swift:568,569
                T("Longbridge 앱에서 모의 계정을 만든 뒤 open.longbridge.com 개발자 센터에서 무료로 받아요. Access Token은 기한이 있어서 지나면 새로 넣어야 해요. 홍콩·중국 종목은 앱이 그 종목을 다루게 되면 같은 키로 받아요.", style = appFont(13f), color = Theme.sub)
                Field("App Key", key) { key = it }   // ios: Views/SettingsView.swift:570
                Field("App Secret", secret) { secret = it }   // ios: Views/SettingsView.swift:571
                Field("Access Token", token) { token = it }   // ios: Views/SettingsView.swift:572
                val t = { v: String -> v.trim() }   // ios: Views/SettingsView.swift:573
                val ok = t(key).isNotEmpty() && t(secret).isNotEmpty() && t(token).isNotEmpty()   // ios: Views/SettingsView.swift:574
                // ios: Views/SettingsView.swift:575
                PrimaryButton(title = if (busy) "확인 중…" else "연결하기", color = if (ok) Theme.teal else Theme.muted) {
                    if (!ok || busy) return@PrimaryButton   // ios: Views/SettingsView.swift:576
                    LongbridgeKeys.save(LongbridgeKeys.Keys(key = t(key), secret = t(secret), token = t(token)))   // ios: Views/SettingsView.swift:577
                    secret = ""; token = ""   // ios: Views/SettingsView.swift:578
                    scope.launch { check() }   // ios: Views/SettingsView.swift:579
                }
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:581
            }
        }
    }
}

/** 오픈 API 맨 위 그림: 내 폰 · naeilo 서버 · 토스 시세. 키를 넣기 전에는 끊겨 있고, 넣으면 이어진다
 *  (키는 이 폰 보관함에만 두고, 중계 서버는 저장·기록 없이 넘기기만 한다. docs/toss-relay.md) */
@Composable
// ios: Views/SettingsView.swift:613,614,616
fun TossLinkMap(on: Boolean) {
    // ios: Views/SettingsView.swift:638 .animation(.easeInOut(duration: 0.4), value: on): 색이 0.4초에 바뀐다
    val teal = animateColorAsState(if (on) Theme.teal else Theme.track, tween(400), label = "node").value
    val icon = animateColorAsState(if (on) Color.White else Theme.muted, tween(400), label = "icon").value
    val lockC = animateColorAsState(if (on) Theme.teal else Theme.muted, tween(400), label = "lock").value

    // ios: Views/SettingsView.swift:641,642,656,657
    @Composable
    fun androidx.compose.foundation.layout.RowScope.Node(icn: String, name: String, note: String, lock: Boolean) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ios: Views/SettingsView.swift:643,644,645
            Box(Modifier.size(52.dp, 52.dp).background(teal, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Sym(icn, 22f, icon, scaled = false)
                // ios: Views/SettingsView.swift:646,647,648,649,650
                if (lock) Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(22.dp, 22.dp).background(Theme.card, CircleShape).border(2.dp, lockC, CircleShape), contentAlignment = Alignment.Center) {
                    Sym("lock.fill", 10f, lockC, scaled = false)
                }
            }
            T(name, style = appFont(12f, FontWeight.Bold).copy(textAlign = TextAlign.Center))   // ios: Views/SettingsView.swift:653
            T(note, style = appFont(11f, if (lock) FontWeight.SemiBold else FontWeight.Normal).copy(textAlign = TextAlign.Center), color = if (lock && on) Theme.teal else Theme.muted)   // ios: Views/SettingsView.swift:654
        }
    }

    // ios: Views/SettingsView.swift:617
    Card {
        // ios: Views/SettingsView.swift:618
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Node("iphone", "내 폰", "열쇠는 나만", lock = true)   // ios: Views/SettingsView.swift:619
            TossLinkLine(on = on)   // ios: Views/SettingsView.swift:620
            Node("server.rack", "naeilo 서버", "통로만 해요", lock = false)   // ios: Views/SettingsView.swift:621
            TossLinkLine(on = on)   // ios: Views/SettingsView.swift:622
            Node("chart.line.uptrend.xyaxis", "증권사 시세", " ", lock = false)   // ios: Views/SettingsView.swift:623
        }
        // ios: Views/SettingsView.swift:625,626
        T(if (on) "이어졌어요" else "키를 넣으면 이어져요", Modifier.fillMaxWidth(), style = appFont(13f, FontWeight.SemiBold).copy(textAlign = TextAlign.Center), color = if (on) Theme.teal else Theme.muted)
        T(if (on) "지금 이어져 있어요." else "어디에도 남지 않아요.", Modifier.padding(top = 4.dp), style = appFont(17f, FontWeight.ExtraBold))   // ios: Views/SettingsView.swift:627
        // ios: Views/SettingsView.swift:628,629
        T(if (on) "그만두려면 아래 키 지우기를 누르면 돼요." else "키는 암호화되어 지나가기만 해요.", style = appFont(13f), color = Theme.sub)
        // ios: Views/SettingsView.swift:630,636
        Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(if (on) "chart.line.uptrend.xyaxis" else "key.fill", 15f, Theme.teal, scaled = false)   // ios: Views/SettingsView.swift:631
            // ios: Views/SettingsView.swift:632,633,634
            T(if (on) "현재가·일봉·환율 정보만 받아 왔어요." else "naeilo에 추가한 주식의 현재가·일봉·환율 정보만 받아올 거예요.", Modifier.weight(1f).padding(end = 0.dp), style = appFont(13f))
        }
    }
}

/** 두 칸 사이 줄: 끊김은 점선 + 빨간 끊긴 표시, 이어짐은 청록 실선 위로 점이 오른쪽에서 왼쪽(내 폰 쪽)으로 흐른다 */
// ios: Views/SettingsView.swift:662,663,667
@Composable
private fun TossLinkLine(on: Boolean) {
    val still = LocalReduceMotion.current   // ios: Views/SettingsView.swift:664
    val teal = Theme.teal; val muted = Theme.muted; val up = Theme.up
    // ios: Views/SettingsView.swift:665,672,673,674 시세가 토스에서 내 폰 쪽으로 온다: 1.4초에 22 → 0 을 되풀이
    val flow = if (on && !still) rememberInfiniteTransition(label = "flow").animateFloat(22f, 0f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x").value else 11f
    // ios: Views/SettingsView.swift:668,680,681
    Canvas(Modifier.size(30.dp, 52.dp).semantics { invisibleToUser() }) {
        val mid = size.height / 2
        // ios: Views/SettingsView.swift:669
        if (on) {
            drawLine(teal, Offset(0f, mid), Offset(size.width, mid), 2.dp.toPx())   // ios: Views/SettingsView.swift:670
            drawOval(teal, Offset(flow.dp.toPx(), mid - 4.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()))   // ios: Views/SettingsView.swift:671 (왼쪽 끝 + x)
        } else {   // ios: Views/SettingsView.swift:675
            // ios: Views/SettingsView.swift:684,685 가운데를 지나는 가로줄
            fun line(band: Float): Pair<Offset, Offset> { val y = (size.height - band) / 2 + band / 2; return Offset(0f, y) to Offset(size.width, y) }
            // ios: Views/SettingsView.swift:676 높이 2 칸 가운데에 굵기 2 점선
            val (a, b) = line(2.dp.toPx())
            drawLine(muted.copy(alpha = 0.7f), a, b, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            // ios: Views/SettingsView.swift:677 빨간 끊긴 표시 (2×16, 25도, 왼쪽에서 14)
            rotate(25f, Offset(14.dp.toPx() + 1.dp.toPx(), mid)) { drawRect(up.copy(alpha = 0.9f), Offset(14.dp.toPx(), mid - 8.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), 16.dp.toPx())) }
        }
    }
}

// 앱 아이콘 바꾸기: 기본 · 내일의 별 + 인물 5. 안드로이드는 런처 별칭(activity-alias)을 켜고 끈다
// 인물 아이콘은 그 인물을 만나야 열린다 (세리는 처음부터, 나머지는 인터미션 1~4주차. 쉼터와 같은 단계)
// ios: Views/SettingsView.swift:691,692,706
@Composable
fun AppIconPicker(m: AppModel) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(AppIconPicker.alternateIconName(ctx)) }   // ios: Views/SettingsView.swift:697

    fun open(f: Int?): Boolean = f?.let { m.friendOn(it) } ?: true   // ios: Views/SettingsView.swift:700
    // ios: Views/SettingsView.swift:701,702,703
    fun set(id: String?) {
        if (current == id) return
        AppIconPicker.setAlternateIconName(ctx, id); current = id
    }

    // 시안 조작으로 단계를 되돌려 지금 아이콘이 다시 잠기면 기본 아이콘으로 돌린다
    // ios: Views/SettingsView.swift:733,734
    LaunchedEffect(Unit) { current?.let { c -> AppIconPicker.icons.firstOrNull { it.id == c }?.let { ic -> if (!open(ic.friend)) set(null) } } }
    // ios: Views/SettingsView.swift:707,729,730,731 (3열, 칸 사이 8, 줄 사이 12)
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIconPicker.icons.chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ios: Views/SettingsView.swift:708
                line.forEach { ic ->
                    val on = current == ic.id; val ok = open(ic.friend)   // ios: Views/SettingsView.swift:709
                    // ios: Views/SettingsView.swift:710,721,723,724,725,726
                    Column(
                        Modifier.weight(1f).then(if (ok) Modifier.plainClickable { set(ic.id) } else Modifier.semantics { disabled() })
                            .semantics(mergeDescendants = true) { contentDescription = if (ok) "${ic.name} 아이콘" else "${ic.name} 아이콘, 인터미션 ${ic.friend ?: 0}주차에 열려요"; selected = on }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),   // ios: Views/SettingsView.swift:711
                    ) {
                        // ios: Views/SettingsView.swift:712,713,714,715,716,717
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)).border(if (on) 3.dp else 1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.graphics.ImageBitmap.imageResource(com.naeilo.app.ui.drawableId(ctx, ic.prev)), null, Modifier.size(64.dp),
                                colorFilter = if (ok) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }.also { it.timesAssign(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, -0.35f * 255, 0f, 1f, 0f, 0f, -0.35f * 255, 0f, 0f, 1f, 0f, -0.35f * 255, 0f, 0f, 0f, 1f, 0f))) }),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                            if (!ok) Sym("lock.fill", 20f, Color.White, scaled = false)
                        }
                        // ios: Views/SettingsView.swift:718,719
                        FitText(if (ok) ic.name else "인터미션 ${ic.friend ?: 0}주차", appFont(if (ok) 13f else 11f, if (on) FontWeight.Bold else FontWeight.Normal), 0.8f, maxLines = 1,
                          color = if (on) Theme.ink else if (ok) Theme.sub else Theme.muted)
                    }
                }
                repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 아이콘 목록과 바꾸기 (UIApplication.alternateIconName 자리: 런처 별칭) */
object AppIconPicker {
    class Icon(val id: String?, val name: String, val prev: String, val friend: Int?)
    // ios: Views/SettingsView.swift:693
    val icons: List<Icon> = listOf(
        Icon(null, "내일의 별", "iconprev_star", null), Icon("AppIcon-seri", "세리", "iconprev_seri", 0), Icon("AppIcon-sio", "시오", "iconprev_sio", 1),   // ios: Views/SettingsView.swift:694
        Icon("AppIcon-seonbae", "선배", "iconprev_seonbae", 2), Icon("AppIcon-ir", "이르", "iconprev_ir", 3), Icon("AppIcon-sua", "수아", "iconprev_sua", 4),   // ios: Views/SettingsView.swift:695
    )
    // ios: Views/SettingsView.swift:698
    fun currentName(ctx: Context): String = icons.firstOrNull { it.id == alternateIconName(ctx) }?.name ?: "내일의 별"

    /** 별칭 이름: 기본은 .IconDefault, 인물은 .Icon_<인물> (AndroidManifest) */
    private fun alias(ctx: Context, id: String?) = ComponentName(ctx.packageName, "com.naeilo.app." + (id?.let { "Icon_" + it.removePrefix("AppIcon-") } ?: "IconDefault"))

    /** 지금 켜진 별칭 (기본이면 null) */
    fun alternateIconName(ctx: Context): String? = icons.firstOrNull { it.id != null && ctx.packageManager.getComponentEnabledSetting(alias(ctx, it.id)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }?.id

    /** 고른 별칭만 켜고 나머지는 끈다. 앱은 닫지 않는다 (런처가 아이콘을 바꾸는 데 몇 초 걸릴 수 있다) */
    fun setAlternateIconName(ctx: Context, id: String?) {
        val pm = ctx.packageManager
        pm.setComponentEnabledSetting(alias(ctx, id), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        icons.filter { it.id != id }.forEach {
            pm.setComponentEnabledSetting(alias(ctx, it.id), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

/** 설정 > 앱 아이콘 › 고르는 화면 (설정 첫 화면에서 격자를 뺐다) */
// ios: Views/SettingsView.swift:740,741
@Composable
fun AppIconScreen(m: AppModel) {
    // ios: Views/SettingsView.swift:742,743,747,749,750
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("앱 아이콘", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:744
        T("인물 아이콘은 그 인물을 만나면 열려요.", style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:745
        AppIconPicker(m)   // ios: Views/SettingsView.swift:746
    }
}

