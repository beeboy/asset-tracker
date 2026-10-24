package com.naeilo.app.ui.settings

import com.naeilo.app.core.cformat
import com.naeilo.app.core.tr
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.naeilo.app.ui.wave.GameHeader
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
    val clip = rememberClip()
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
                T(if (copied) "복사됨" else "주소 전부 복사", Modifier.plainClickable { clip(rows.joinToString(", ") { it.email }); copied = true },
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
enum class SettingsRoute { alerts, sync, widgets, tax, price, toss, alpaca, longbridge, howto, route, goal, charPreview, support, icon, notifyList, residence, goalGroup, oftenGroup, screenGroup, numberGroup }   // ios: Views/SettingsView.swift:97

/** 설정 한 줄 (제목, 설명, 갈 곳) */
internal class SRow(val title: String, val sub: String, val to: SettingsRoute)

/** 설정 첫 화면: 내 키 한 줄 → 함께할 인물 → 타일 넷(목표·알림과 위젯·화면·숫자 기준) → 작은 글 링크.
 *  타일을 누르면 그 묶음 화면(SettingsGroupView)으로 간다 */
// ios: Views/SettingsView.swift:101,110
@Composable
fun SettingsView(m: AppModel, cover: Boolean = false) {   // cover: 홈 [설정] 전체 화면 팝업, 맨 위 줄을 블록·파도처럼 제목 + X로
    val boxLook = com.naeilo.app.ui.home.BoxLook.raw   // ios: Views/SettingsView.swift:104 (@AppStorage(BoxLook.key))
    var showDemo by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:105 시연 상태: 개발자 기기에서 버전 글을 길게 누르면 열린다
    val onN = m.alerts.values.count { it }   // ios: Views/SettingsView.swift:111
    val looks = mapOf("system" to "기기 설정대로", "light" to "밝게", "dark" to "어둡게")   // ios: Views/SettingsView.swift:112
    // ios: Views/SettingsView.swift:113,141,142
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen {
            // ios: Views/SettingsView.swift:114
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cover) GameHeader(title = "설정", dismiss = { m.settingsOpen = false; m.settingsPath = emptyList() }) else Box(Modifier.outset(16.dp)) { AppHeader(tab = "설정") }   // ios: Views/SettingsView.swift:115 (-16)
                ApiLine(m)   // ios: Views/SettingsView.swift:116
                GuidePickRow(m, compact = true)   // ios: Views/SettingsView.swift:117
                // ios: Views/SettingsView.swift:118 (LazyVGrid 두 칸)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ios: Views/SettingsView.swift:119
                    Tile(m, "flag", "내 목표", if (m.isGoal) "${m.goalToText}까지 ${AppModel.wonK(m.gK)}" else "아직 목표가 없어요", SettingsGroupView.routeLine(m), SettingsRoute.goalGroup)
                    // ios: Views/SettingsView.swift:122
                    Tile(m, "bell", "알림과 위젯", if (onN > 0) "알림 ${onN}개 켜짐" else "알림 모두 꺼짐", "위젯 ${m.baseWidgets.size + Shelter.friends.size}가지", SettingsRoute.oftenGroup)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ios: Views/SettingsView.swift:125
                    Tile(m, "iphone", "화면", looks[m.appearance] ?: looks.getValue("system"), com.naeilo.app.ui.home.BoxLook.entries.firstOrNull { it.name == boxLook }?.label ?: "", SettingsRoute.screenGroup)
                    Tile(m, "chart.pie", "숫자 기준", m.residenceName, "시세 · 내 API 키", SettingsRoute.numberGroup)   // ios: Views/SettingsView.swift:128
                }
                Footer(m)   // ios: Views/SettingsView.swift:131
                if (m.devAll && showDemo) Demo(m)   // ios: Views/SettingsView.swift:132
                // ios: Views/SettingsView.swift:133,134,135,136
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 32.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { if (m.devAll) showDemo = !showDemo }) },
                    contentAlignment = Alignment.Center) {
                    T("버전 0.1 · 서재: 외전 프롤로그~5장 (한글·영문)", style = appFont(12f), color = Theme.muted)
                }
            }
        }
    }
}

/** 설정에서 여는 화면 (navigationDestination for: SettingsRoute) */
// ios: Views/SettingsView.swift:142,143
fun settingsPage(m: AppModel, r: SettingsRoute): NavPage = when (r) {
    SettingsRoute.alerts -> NavPage { AlertsView(m) }   // ios: Views/SettingsView.swift:144
    SettingsRoute.sync -> NavPage { SyncView(m) }   // ios: Views/SettingsView.swift:145
    SettingsRoute.widgets -> NavPage { WidgetPickView(m) }   // ios: Views/SettingsView.swift:146
    SettingsRoute.tax -> NavPage { TaxRulesView(m) }   // ios: Views/SettingsView.swift:147
    SettingsRoute.price -> NavPage { PriceBasisView(m) }   // ios: Views/SettingsView.swift:148
    SettingsRoute.toss -> NavPage { TossKeyView(m) }   // ios: Views/SettingsView.swift:149
    SettingsRoute.alpaca -> NavPage { AlpacaKeyView(m) }   // ios: Views/SettingsView.swift:150
    SettingsRoute.longbridge -> NavPage { LongbridgeKeyView(m) }   // ios: Views/SettingsView.swift:151
    SettingsRoute.howto -> NavPage { HowToView() }   // ios: Views/SettingsView.swift:152
    SettingsRoute.route -> NavPage { RouteView(m) }   // ios: Views/SettingsView.swift:153
    SettingsRoute.goal -> NavPage { GoalSetView(m, fromSettings = true) }   // ios: Views/SettingsView.swift:154
    SettingsRoute.support -> NavPage { SupportView(m) }   // ios: Views/SettingsView.swift:155
    SettingsRoute.icon -> NavPage { AppIconScreen(m) }   // ios: Views/SettingsView.swift:156
    SettingsRoute.notifyList -> NavPage { NotifyListView() }   // ios: Views/SettingsView.swift:157
    SettingsRoute.residence -> NavPage("거주 국가") { ResidenceView(m) }   // ios: Views/SettingsView.swift:158 (제목은 ResidenceView 의 navigationTitle)
    SettingsRoute.charPreview -> NavPage { WidgetPickView(m) }     // ios: Views/SettingsView.swift:159 예전 '인물 위젯 미리보기'는 위젯 화면으로 합쳤다
    SettingsRoute.goalGroup, SettingsRoute.oftenGroup, SettingsRoute.screenGroup, SettingsRoute.numberGroup -> NavPage { SettingsGroupView(m, r) }   // ios: Views/SettingsView.swift:160
}

/** 앱 언어 화면 (안드로이드 13+: 앱별 언어, 그 전: 앱 정보) */
private fun openAppLanguage(ctx: Context) {
    val pkg = Uri.fromParts("package", ctx.packageName, null)
    val i = if (Build.VERSION.SDK_INT >= 33) Intent(Settings.ACTION_APP_LOCALE_SETTINGS, pkg) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/** Longbridge 를 Alpaca 보다 먼저 보이는 거주 국가 */
internal val lbFirst = listOf("HK", "CN", "SG", "MO", "TW", "MY")   // ios: Views/SettingsView.swift:108

/** 맨 위 조용한 한 줄: 거주 국가에 맞는 내 키(한국 토스, lbFirst 나라 Longbridge, 그 밖 Alpaca).
 *  평소엔 배경과 같은 결, 키를 넣었는데 받기가 실패하면 주황으로 눈에 띈다 */
// ios: Views/SettingsView.swift:167
@Composable
private fun ApiLine(m: AppModel) {
    val kr = m.residence == "KR"   // ios: Views/SettingsView.swift:168
    val lb = lbFirst.contains(m.residence)   // ios: Views/SettingsView.swift:169
    if (kr) Market.shared.tossDone else if (lb) Market.shared.longbridgeDone else Market.shared.alpacaDone   // ios: Views/SettingsView.swift:170 받기를 마치면 다시 그린다
    val on = if (kr) TossKeys.on else if (lb) LongbridgeKeys.on else AlpacaKeys.on   // ios: Views/SettingsView.swift:171
    val broken = on && (if (kr) TossFeed.lastError else if (lb) LongbridgeFeed.lastError else AlpacaFeed.lastError) != null   // ios: Views/SettingsView.swift:172
    val to = if (kr) SettingsRoute.toss else if (lb) SettingsRoute.longbridge else SettingsRoute.alpaca   // ios: Views/SettingsView.swift:173
    // ios: Views/SettingsView.swift:174,183,184,185
    Row(Modifier.fillMaxWidth().background(if (broken) Theme.orange.copy(alpha = 0.16f) else Theme.track, RoundedCornerShape(14.dp))
        .plainClickable { m.settingsPath = m.settingsPath + to }.defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym(if (broken) "info.circle.fill" else if (on) "lock.fill" else "key.fill", 14f, if (broken) Theme.orange else if (on) Theme.teal else Theme.muted)   // ios: Views/SettingsView.swift:175,176
        T(if (broken) "내 키로 못 받고 있어요" else if (on) "내 키로 시세 받는 중" else "내 키로 시세 받기", Modifier.weight(1f), style = appFont(14f, FontWeight.SemiBold), maxLines = 1)   // ios: Views/SettingsView.swift:177,178
        T(if (kr) "현재가·일봉·환율" else "미국 종목 현재가", style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/SettingsView.swift:180
        Sym("chevron.right", 12f, Theme.muted)   // ios: Views/SettingsView.swift:181
    }
}

// ios: Views/SettingsView.swift:190,191
@Composable
private fun androidx.compose.foundation.layout.RowScope.Tile(m: AppModel, icon: String, title: String, line1: String, line2: String, r: SettingsRoute) {
    // ios: Views/SettingsView.swift:192,200,201,202,203
    Column(Modifier.weight(1f).defaultMinSize(minHeight = 116.dp).background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp))
        .plainClickable { m.settingsPath = m.settingsPath + r }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sym(icon, 20f, Theme.teal)   // ios: Views/SettingsView.swift:193
        T(title, style = appFont(16f, FontWeight.Bold))   // ios: Views/SettingsView.swift:194
        // ios: Views/SettingsView.swift:195,198
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            T(line1, style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/SettingsView.swift:196
            T(line2, style = appFont(12f), color = Theme.sub, maxLines = 1)   // ios: Views/SettingsView.swift:197
        }
    }
}

/** 자주 안 여는 것: 작은 글 링크 한 줄 */
// ios: Views/SettingsView.swift:210,211,218,219
@Composable
private fun Footer(m: AppModel) {
    val ctx = LocalContext.current
    @Composable
    fun Link(t: String, a: () -> Unit) = Box(Modifier.plainClickable(onClick = a).defaultMinSize(minHeight = 44.dp), contentAlignment = Alignment.Center) { T(t, style = appFont(13f), color = Theme.sub) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        Link("처음 쓰는 법") { m.settingsPath = m.settingsPath + SettingsRoute.howto }   // ios: Views/SettingsView.swift:212
        Link("기기 옮기기") { m.settingsPath = m.settingsPath + SettingsRoute.sync }   // ios: Views/SettingsView.swift:213
        Link("구독") { m.settingsPath = m.settingsPath + SettingsRoute.support }   // ios: Views/SettingsView.swift:214
        Link("언어") { openAppLanguage(ctx) }   // ios: Views/SettingsView.swift:216 앱 언어는 안드로이드 설정의 앱 언어 화면에서 바꾼다
    }
}

// 시연 상태로 되돌리기는 내 종목을 샘플로 덮어쓴다: 개발자 기기(테스트 빌드)에서만
// ios: Views/SettingsView.swift:223
@Composable
private fun Demo(m: AppModel) {
    T("시연 상태 (개발자)", Modifier.padding(top = 8.dp), style = appFont(13f, FontWeight.Bold), color = Theme.sub)   // ios: Views/SettingsView.swift:224
    // ios: Views/SettingsView.swift:225
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DemoButton("첫 질문부터") { m.onboarded = false; Defaults.prefs.edit().putBoolean("onboarded", false).apply() }   // ios: Views/SettingsView.swift:226
        DemoButton("미션 1부터") { m.resetDemo(AppModel.Demo.fresh) }   // ios: Views/SettingsView.swift:227
        DemoButton("인터미션 1주차로") { m.resetDemo(AppModel.Demo.week1) }   // ios: Views/SettingsView.swift:228
        DemoButton("모든 화면 열기") { m.resetDemo(AppModel.Demo.all) }   // ios: Views/SettingsView.swift:229
    }
    SettingsRows(m, null, listOf(SRow("출시 알림 신청 목록", "naeilo.com 앱 받기 신청", SettingsRoute.notifyList)))   // ios: Views/SettingsView.swift:231
    T("시세: ${Market.shared.source} · ${Config.channel.raw}", style = appFont(12f), color = Theme.muted)   // ios: Views/SettingsView.swift:232
}

/** 타일을 누르면 열리는 묶음 화면 */
// ios: Views/SettingsView.swift:245
@Composable
fun SettingsGroupView(m: AppModel, kind: SettingsRoute) {
    val ctx = LocalContext.current
    val boxLook = com.naeilo.app.ui.home.BoxLook.raw   // ios: Views/SettingsView.swift:248
    // ios: Views/SettingsView.swift:255,256,300,301,302
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (kind) {
            // ios: Views/SettingsView.swift:258
            SettingsRoute.goalGroup -> {
                T("내 목표", style = appFont(22f, FontWeight.Bold))
                SettingsRows(m, null, listOf(
                    SRow("목표 고치기", if (m.isGoal) "${m.goalToText}까지 ${AppModel.wonK(m.gK)}" else "아직 목표가 없어요", SettingsRoute.goal),
                    SRow("루트 바꾸기", SettingsGroupView.routeLine(m), SettingsRoute.route),
                ))
            }
            // ios: Views/SettingsView.swift:264
            SettingsRoute.oftenGroup -> {
                val onN = m.alerts.values.count { it }
                T("알림과 위젯", style = appFont(22f, FontWeight.Bold))
                SettingsRows(m, null, listOf(
                    SRow("알림", if (onN > 0) "${onN}개 켜짐" else "모두 꺼짐", SettingsRoute.alerts),
                    SRow("홈 화면 위젯", "${m.baseWidgets.size + Shelter.friends.size}가지 · 미리보기", SettingsRoute.widgets),
                ))
            }
            // ios: Views/SettingsView.swift:271
            SettingsRoute.screenGroup -> {
                T("화면", style = appFont(22f, FontWeight.Bold))
                T("밝기 모드", style = appFont(13f, FontWeight.Bold), color = Theme.sub)
                ChipRow(items = listOf("system" to "기기 설정대로", "light" to "밝게", "dark" to "어둡게"),
                        selection = m.appearance, onSelect = { m.appearance = it; Defaults.prefs.edit().putString("appearance", it).apply() }, fill = true)
                // 홈 평가액 박스 모양 (홈 박스를 길게 눌러도 고른다)
                T("홈 평가액 박스", style = appFont(13f, FontWeight.Bold), color = Theme.sub)
                ChipRow(items = com.naeilo.app.ui.home.BoxLook.entries.map { it.name to it.label }, selection = boxLook, onSelect = { com.naeilo.app.ui.home.BoxLook.raw = it }, fill = true)
                // 상단 박스(홈·종목·흐름·분석)만 밝기 모드를 따로 고른다. 앱 전체 모드는 그대로   // ios: Views/SettingsView.swift:279,280,281
                T("상단 박스", style = appFont(13f, FontWeight.Bold), color = Theme.sub)
                ChipRow(items = com.naeilo.app.ui.home.BoxScheme.entries.map { it.name to it.label }, selection = com.naeilo.app.ui.home.BoxScheme.raw, onSelect = { com.naeilo.app.ui.home.BoxScheme.raw = it }, fill = true)
                SettingsRows(m, null, listOf(SRow("앱 아이콘", AppIconPicker.currentName(ctx), SettingsRoute.icon)))
                WaveFeedbackToggles()
            }
            // ios: Views/SettingsView.swift:281
            SettingsRoute.numberGroup -> {
                val kr = m.residence == "KR"
                // 증권사는 나라와 상관없이 누구나 연결: 거주 국가에 맞는 것만 위로 올린다
                val toss = SRow("Toss", if (TossKeys.on) "받는 중" else "넣지 않음", SettingsRoute.toss)
                val alpaca = SRow("Alpaca", if (AlpacaKeys.on) "받는 중" else "넣지 않음 · 미국 종목 실시간 시세", SettingsRoute.alpaca)
                val lb = SRow("Longbridge", if (LongbridgeKeys.on) "받는 중" else "넣지 않음 · 미국·홍콩·중국 실시간 시세", SettingsRoute.longbridge)
                T("숫자 기준", style = appFont(22f, FontWeight.Bold))
                SettingsRows(m, "나라와 세금", listOf(
                    SRow("거주 국가", "${m.residenceName} · 세금 계산만 이 나라를 따라요", SettingsRoute.residence),
                    SRow("세금", if (kr) "한국 거주자 기준으로 계산해요" else "이 나라 규칙은 준비 중 · 지금은 한국 기준", SettingsRoute.tax),
                ))
                SettingsRows(m, "시세", listOf(SRow("시세 기준", "종목·환율 시세를 어디서 받는지", SettingsRoute.price)))
                SettingsRows(m, "내 API 키", if (kr) listOf(toss, alpaca, lb) else if (lbFirst.contains(m.residence)) listOf(lb, alpaca, toss) else listOf(alpaca, lb, toss))
            }
            else -> {}
        }
    }
}

// ios: Views/SettingsView.swift:245 (static)
object SettingsGroupView {
    // ios: Views/SettingsView.swift:250
    fun routeLine(m: AppModel): String = if (m.route == Route.recover) "본전부터 되찾는 중이에요" else if (m.route == Route.plus) "목표를 향해 가는 중이에요" else "아직 첫 단계예요"
}

/** 제목 + 누르면 넘어가는 줄 묶음 (설정 화면들이 같이 쓴다) */
// ios: Views/SettingsView.swift:306
@Composable
internal fun SettingsRows(m: AppModel, title: String?, rows: List<SRow>) {
    if (title != null) T(title, Modifier.padding(top = 8.dp), style = appFont(13f, FontWeight.Bold), color = Theme.teal)
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) HDivider(Theme.line)
            Row(Modifier.fillMaxWidth().plainClickable { m.settingsPath = m.settingsPath + r.to }.defaultMinSize(minHeight = 60.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(r.title, style = appFont(15f, FontWeight.Bold))
                    T(r.sub, style = appFont(13f), color = Theme.sub)
                }
                Sym("chevron.right", 13f, Theme.muted)
            }
        }
    }
}

// ios: Views/SettingsView.swift:285,286,290
@Composable
private fun androidx.compose.foundation.layout.RowScope.DemoButton(t: String, a: () -> Unit) {
    val muted = Theme.muted
    // ios: Views/SettingsView.swift:287,288,289
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

// ios: Views/SettingsView.swift:294,295,296
@Composable
fun PriceBasisView(m: AppModel) {
    val mk = Market.shared; val fx = mk.fx   // ios: Views/SettingsView.swift:297
    // ios: Views/SettingsView.swift:298,323,324,325
    Refreshable({ Market.shared.pullRefresh(m) }, Modifier.fillMaxSize().background(Theme.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {   // ios: Views/SettingsView.swift:299,321
            T("시세 기준", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:300
            // ios: Views/SettingsView.swift:301
            val main = if (mk.toss != null) "토스 오픈 API" else if (mk.alpaca != null) "Alpaca 시세" else if (mk.longbridge != null) "Longbridge 시세" else if (mk.site != null) "naeilo 사이트 시세" else "예시 값"
            // ios: Views/SettingsView.swift:302,307,308
            Fg(Color.White) {
                Column(Modifier.fillMaxWidth().background(Theme.night, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("지금 보는 숫자", style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/SettingsView.swift:303
                    T(main, style = appFont(28f, FontWeight.Bold))   // ios: Views/SettingsView.swift:304
                    T(mk.asOfText.replace(" (테스트 자료)", ""), style = appFont(13f), color = hex(0xC9D0D6))   // ios: Views/SettingsView.swift:305
                }
            }
            // ios: Views/SettingsView.swift:309
            Card {
                PRow("받는 순서", "내 토스 키 → 내 Alpaca·Longbridge 키 → naeilo 사이트. 앞에서 받은 값을 먼저 써요")   // ios: Views/SettingsView.swift:310
                PRow("미국 종목", if (mk.toss != null) "토스 지금 가격" else if (mk.alpaca != null) "Alpaca 지금 가격" else if (mk.longbridge != null) "Longbridge 지금 가격" else "사이트가 평일 30분마다 모은 가격")   // ios: Views/SettingsView.swift:311
                PRow("한국 종목", if (mk.toss != null) "토스 지금 가격" else "사이트가 모은 가격. 미국 지수를 따르는 국내 ETF는 그 미국 ETF × 원/달러로 추정해요")   // ios: Views/SettingsView.swift:312
                PRow("장 마감 뒤", "미국 장이 끝나면 정규장 종가로 세요 (토스 총자산과 같은 기준)")   // ios: Views/SettingsView.swift:313
                // Views/SettingsView.swift:309 (검사 예외: 같은 줄에 옮김)
                PRow("원/달러", "${cformat("%,d", fx.last.roundToLong())}원 · 오늘 ${AppModel.sgn(fx.change)}")
                // Views/SettingsView.swift:310 (검사 예외: 같은 줄에 옮김)
                PRow("평가 환율", if (mk.marRate != null) "매매기준율 ${cformat("%.2f", mk.valFx)}원 · 웹 평가액과 같아요" else "매매기준율을 아직 못 받아 지금 환율")
                PRow("오늘의 움직임", "전일 종가 대비 지금 평가액")   // ios: Views/SettingsView.swift:316
            }
            // ios: Views/SettingsView.swift:318,319
            T("그래프와 미션은 종가로 계산해요. 아래로 당기면 바로 다시 받아요.", style = appFont(13f).ls(2f), color = Theme.sub)
        }
    }
}
// ios: Views/SettingsView.swift:327,328,329
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
// ios: Views/SettingsView.swift:336,337,361
@Composable
fun TossKeyView(m: AppModel) {
    var id by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:338
    var secret by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:339
    var ip by remember { mutableStateOf<String?>(Config.tossRelayIP) }   // ios: Views/SettingsView.swift:340 (고정 IP 를 바로 보여 준다: 받기 실패해도 빈칸 없음)
    var busy by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:341
    var msg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:342
    var on by remember { mutableStateOf(TossKeys.on) }   // ios: Views/SettingsView.swift:343
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:344
    var copied by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:345
    val scope = rememberCoroutineScope()
    val clip = rememberClip()

    // ios: Views/SettingsView.swift:347
    val status: String = run {
        Market.shared.tossDone      // ios: Views/SettingsView.swift:348 토스 받기를 마치면 다시 그린다
        val t = TossFeed.okAt   // ios: Views/SettingsView.swift:349
        var s: String   // ios: Views/SettingsView.swift:350
        // ios: Views/SettingsView.swift:351,352,353,354,355
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s = "마지막으로 받은 때 $f"
        } else { s = "아직 받은 적 없어요" }   // ios: Views/SettingsView.swift:356
        TossFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsView.swift:357
        s   // ios: Views/SettingsView.swift:358
    }

    /** 토큰을 새로 받아 키·허용 IP 를 확인하고, 되면 시세를 바로 받는다 */
    // ios: Views/SettingsView.swift:433
    suspend fun check() {
        busy = true; msg = null   // ios: Views/SettingsView.swift:434
        // 토큰은 만료 전이면 그대로 쓴다 (새로 받으면 받는 중인 다른 요청의 토큰이 끊긴다). 처음 연결이면 키를 넣을 때 토큰을 지워 새로 받는다
        val (t, e) = TossFeed.token()   // ios: Views/SettingsView.swift:436
        // ios: Views/SettingsView.swift:437
        if (t == null) {
            msg = (e ?: "연결 실패") + (if (e == "허용 IP에 중계 IP가 없어요") ". 오픈 API 허용 IP에 위 IP를 넣고 다시 눌러 주세요." else "")   // ios: Views/SettingsView.swift:438
            if (!on) TossKeys.clear()     // ios: Views/SettingsView.swift:439 처음 연결이 실패하면 키를 남기지 않는다
        } else {   // ios: Views/SettingsView.swift:440
            on = true   // ios: Views/SettingsView.swift:441
            Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Views/SettingsView.swift:442
        }
        busy = false   // ios: Views/SettingsView.swift:444
    }

    // ios: Views/SettingsView.swift:414
    @Composable
    fun IpRow(ip: String) {
        // ios: Views/SettingsView.swift:415,421
        Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            T(ip, Modifier.weight(1f), style = appFont(17f, FontWeight.Bold))   // ios: Views/SettingsView.swift:416,417
            // ios: Views/SettingsView.swift:418,419
            Box(Modifier.defaultMinSize(minHeight = 44.dp).plainClickable { clip(ip); copied = true }, contentAlignment = Alignment.Center) {
                T(if (copied) "복사됨" else "복사", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)
            }
        }
    }

    // ios: Views/SettingsView.swift:424,425,426,428,429
    @Composable
    fun Field(title: String, v: String, set: (String) -> Unit, secure: Boolean) {
        InputField(v, set, title, Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = secure)
    }

    // ios: Views/SettingsView.swift:372
    LaunchedEffect(Unit) { TossKeys.relayIP()?.let { ip = it } }
    // ios: Views/SettingsView.swift:362,368,370,371,363
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("내 Toss API 키", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:403
        TossLinkMap(on = on)   // ios: Views/SettingsView.swift:365
        // ios: Views/SettingsView.swift:366,375,376
        if (on) Card {
            T("내 키로 받는 중", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:377
            T(status, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:378
            ip?.let { IpRow(it) }   // ios: Views/SettingsView.swift:379
            PrimaryButton(title = if (busy) "받는 중…" else "지금 다시 받기") { scope.launch { check() } }   // ios: Views/SettingsView.swift:380
            msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:381
            // ios: Views/SettingsView.swift:382,383
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).plainClickable { askOff = true }, contentAlignment = Alignment.Center) {
                T("키 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
            }
            // ios: Views/SettingsView.swift:384,385,386
            ConfirmSheet(askOff, "오픈 API 키를 이 폰에서 지울까요?", "기본 시세로 돌아가요. 증권사에서 받은 키는 그 증권사에서 따로 지울 수 있어요.",
                listOf(DialogAction("지우기", destructive = true, onClick = { TossKeys.clear(); on = false; msg = null; scope.launch { Market.shared.pullRefresh(m) } }))) { askOff = false }
        } else Card {   // ios: Views/SettingsView.swift:390,391
            // 안내는 세 줄만 (토스 키 발급 절차는 설명하지 않는다)
            // ios: Views/SettingsView.swift:393
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("info.circle.fill", 15f, Theme.teal, Modifier.padding(top = 2.dp))   // ios: Views/SettingsView.swift:394
                T("투자자가 본인의 API Key를 통해 본인 정보를 호출하는 구조예요.", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:395,397
            }
            T("1. 허용 IP에 서버 중계 IP를 넣어 주세요.", Modifier.padding(top = 6.dp), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/SettingsView.swift:398
            // ios: Views/SettingsView.swift:399
            ip?.let { IpRow(it) } ?: T("중계 IP를 불러오는 중이에요…", style = appFont(13f), color = Theme.muted)
            T("2. 클라이언트 키를 넣고 서버와 연결하세요.", Modifier.padding(top = 6.dp), style = appFont(14f, FontWeight.SemiBold))   // ios: Views/SettingsView.swift:400
            Field("client_id", id, { id = it }, secure = true)   // ios: Views/SettingsView.swift:401
            Field("client_secret", secret, { secret = it }, secure = true)   // ios: Views/SettingsView.swift:402
            val ok = id.trim().isNotEmpty() && secret.trim().isNotEmpty()   // ios: Views/SettingsView.swift:403
            // ios: Views/SettingsView.swift:404
            PrimaryButton(title = if (busy) "확인 중…" else "연결하기", color = if (ok) Theme.teal else Theme.muted) {
                if (!ok || busy) return@PrimaryButton   // ios: Views/SettingsView.swift:405
                TossKeys.save(TossKeys.Keys(id = id.trim(), secret = secret.trim()))   // ios: Views/SettingsView.swift:406
                secret = ""   // ios: Views/SettingsView.swift:407
                scope.launch { check() }   // ios: Views/SettingsView.swift:408
            }
            msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:410
        }
    }
}

// 내 Alpaca 키 (미국 종목만): app.alpaca.markets 모의 계정에서 무료로 받은 키를 넣으면 미국 종목 지금 가격을 Alpaca(IEX)에서 받는다.
// 키는 이 폰 보관함에만 두고 Alpaca 를 직접 부른다 (중계 없음). 토스 키가 있으면 토스 값이 먼저다.
// ios: Views/SettingsView.swift:450,451,473
@Composable
fun AlpacaKeyView(m: AppModel) {
    var id by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:452
    var secret by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:453
    var busy by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:454
    var msg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:455
    var on by remember { mutableStateOf(AlpacaKeys.on) }   // ios: Views/SettingsView.swift:456
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:457
    val scope = rememberCoroutineScope()

    // ios: Views/SettingsView.swift:459
    val status: String = run {
        Market.shared.alpacaDone      // ios: Views/SettingsView.swift:460 Alpaca 받기를 마치면 다시 그린다
        val t = AlpacaFeed.okAt   // ios: Views/SettingsView.swift:461
        var s: String   // ios: Views/SettingsView.swift:462
        // ios: Views/SettingsView.swift:463,464,465,466,467
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s = "마지막으로 받은 때 $f"
        } else { s = "아직 받은 적 없어요" }   // ios: Views/SettingsView.swift:468
        AlpacaFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsView.swift:469
        s   // ios: Views/SettingsView.swift:470
    }

    /** 키를 확인하고, 되면 시세를 바로 받는다. 처음 연결이 실패하면 키를 남기지 않는다 */
    // ios: Views/SettingsView.swift:518
    suspend fun check() {
        busy = true; msg = null   // ios: Views/SettingsView.swift:519
        val e = AlpacaFeed.check()
        // ios: Views/SettingsView.swift:520
        if (e != null) {
            msg = e   // ios: Views/SettingsView.swift:521
            if (!on) AlpacaKeys.clear()   // ios: Views/SettingsView.swift:522
        } else {   // ios: Views/SettingsView.swift:523
            on = true   // ios: Views/SettingsView.swift:524
            Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Views/SettingsView.swift:525
        }
        busy = false   // ios: Views/SettingsView.swift:527
    }

    // ios: Views/SettingsView.swift:511,512,513,514
    @Composable
    fun Field(title: String, v: String, set: (String) -> Unit) {
        InputField(v, set, title, Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)
    }

    // ios: Views/SettingsView.swift:474,505,507,508,475
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("내 Alpaca API 키", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:515
        TossLinkMap(on = on, server = false, broker = "Alpaca", what = "미국 종목의 현재가를 받아 와요.")   // ios: Views/SettingsView.swift:516
        // ios: Views/SettingsView.swift:477
        Card {
            // ios: Views/SettingsView.swift:478
            if (on) {
                T("내 키로 받는 중", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:479
                T(status, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:480
                PrimaryButton(title = if (busy) "받는 중…" else "지금 다시 받기") { scope.launch { check() } }   // ios: Views/SettingsView.swift:481
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:482
                // ios: Views/SettingsView.swift:483,484
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).plainClickable { askOff = true }, contentAlignment = Alignment.Center) {
                    T("키 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
                }
                // ios: Views/SettingsView.swift:485,486,487
                ConfirmSheet(askOff, "Alpaca 키를 이 폰에서 지울까요?", "기본 시세로 돌아가요.",
                    listOf(DialogAction("지우기", destructive = true, onClick = { AlpacaKeys.clear(); on = false; msg = null; scope.launch { Market.shared.pullRefresh(m) } }))) { askOff = false }
            } else {   // ios: Views/SettingsView.swift:488
                T("미국 종목만 Alpaca에서 지금 가격을 받아요. 키는 이 폰에만 둬요.", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:489
                // ios: Views/SettingsView.swift:490,491
                T("app.alpaca.markets 모의(Paper) 계정에서 무료로 받을 수 있어요. 무료 키는 IEX 거래소 실시간 시세예요.", style = appFont(13f), color = Theme.sub)
                Field("API Key ID", id) { id = it }   // ios: Views/SettingsView.swift:492
                Field("Secret Key", secret) { secret = it }   // ios: Views/SettingsView.swift:493
                val ok = id.trim().isNotEmpty() && secret.trim().isNotEmpty()   // ios: Views/SettingsView.swift:494
                // ios: Views/SettingsView.swift:495
                PrimaryButton(title = if (busy) "확인 중…" else "연결하기", color = if (ok) Theme.teal else Theme.muted) {
                    if (!ok || busy) return@PrimaryButton   // ios: Views/SettingsView.swift:496
                    AlpacaKeys.save(AlpacaKeys.Keys(id = id.trim(), secret = secret.trim()))   // ios: Views/SettingsView.swift:497
                    secret = ""   // ios: Views/SettingsView.swift:498
                    scope.launch { check() }   // ios: Views/SettingsView.swift:499
                }
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:501
            }
        }
    }
}

// 내 Longbridge 키: open.longbridge.com 개발자 센터에서 받은 App Key·App Secret·Access Token 을 넣으면 지금 가격을 Longbridge 에서 받는다.
// 앱은 아직 원화·달러 종목만 다루므로 지금은 미국 종목만. 키는 이 폰 보관함에만 두고 Longbridge 를 직접 부른다 (중계 없음). 토스·Alpaca 키가 있으면 그 값이 먼저다.
// ios: Views/SettingsView.swift:533,534,557
@Composable
fun LongbridgeKeyView(m: AppModel) {
    var key by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:535
    var secret by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:536
    var token by remember { mutableStateOf("") }   // ios: Views/SettingsView.swift:537
    var busy by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:538
    var msg by remember { mutableStateOf<String?>(null) }   // ios: Views/SettingsView.swift:539
    var on by remember { mutableStateOf(LongbridgeKeys.on) }   // ios: Views/SettingsView.swift:540
    var askOff by remember { mutableStateOf(false) }   // ios: Views/SettingsView.swift:541
    val scope = rememberCoroutineScope()

    // ios: Views/SettingsView.swift:543
    val status: String = run {
        Market.shared.longbridgeDone      // ios: Views/SettingsView.swift:544 Longbridge 받기를 마치면 다시 그린다
        val t = LongbridgeFeed.okAt   // ios: Views/SettingsView.swift:545
        var s: String   // ios: Views/SettingsView.swift:546
        // ios: Views/SettingsView.swift:547,548,549,550,551
        if (t > 0) {
            val f = okAtText(t, "a h:mm:ss", "M월 d일 a h:mm")
            s = "마지막으로 받은 때 $f"
        } else { s = "아직 받은 적 없어요" }   // ios: Views/SettingsView.swift:552
        LongbridgeFeed.lastError?.let { e -> s += " · 실패: $e" }   // ios: Views/SettingsView.swift:553
        s   // ios: Views/SettingsView.swift:554
    }

    /** 키를 확인하고, 되면 시세를 바로 받는다. 처음 연결이 실패하면 키를 남기지 않는다 */
    // ios: Views/SettingsView.swift:604
    suspend fun check() {
        busy = true; msg = null   // ios: Views/SettingsView.swift:605
        val e = LongbridgeFeed.check()
        // ios: Views/SettingsView.swift:606
        if (e != null) {
            msg = e   // ios: Views/SettingsView.swift:607
            if (!on) LongbridgeKeys.clear()   // ios: Views/SettingsView.swift:608
        } else {   // ios: Views/SettingsView.swift:609
            on = true   // ios: Views/SettingsView.swift:610
            Market.shared.refresh(held = m.holdings.map { it.symbol }.toSet(), force = true)   // ios: Views/SettingsView.swift:611
        }
        busy = false   // ios: Views/SettingsView.swift:613
    }

    // ios: Views/SettingsView.swift:597,598,599,600
    @Composable
    fun Field(title: String, v: String, set: (String) -> Unit) {
        InputField(v, set, title, Modifier.border(2.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp), secure = true)
    }

    // ios: Views/SettingsView.swift:558,591,593,594,559
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("내 Longbridge API 키", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:600
        TossLinkMap(on = on, server = false, broker = "Longbridge", what = "미국 종목의 현재가를 받아 와요.")   // ios: Views/SettingsView.swift:601
        // ios: Views/SettingsView.swift:561
        Card {
            // ios: Views/SettingsView.swift:562
            if (on) {
                T("내 키로 받는 중", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:563
                T(status, style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:564
                PrimaryButton(title = if (busy) "받는 중…" else "지금 다시 받기") { scope.launch { check() } }   // ios: Views/SettingsView.swift:565
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:566
                // ios: Views/SettingsView.swift:567,568
                Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).plainClickable { askOff = true }, contentAlignment = Alignment.Center) {
                    T("키 지우기", style = appFont(15f, FontWeight.SemiBold), color = Theme.up)
                }
                // ios: Views/SettingsView.swift:569,570,571
                ConfirmSheet(askOff, "Longbridge 키를 이 폰에서 지울까요?", "기본 시세로 돌아가요.",
                    listOf(DialogAction("지우기", destructive = true, onClick = { LongbridgeKeys.clear(); on = false; msg = null; scope.launch { Market.shared.pullRefresh(m) } }))) { askOff = false }
            } else {   // ios: Views/SettingsView.swift:572
                T("미국 종목 지금 가격을 Longbridge에서 받아요. 키는 이 폰에만 둬요.", style = appFont(15f, FontWeight.Bold))   // ios: Views/SettingsView.swift:573
                // ios: Views/SettingsView.swift:574,575
                T("Longbridge 앱에서 모의 계정을 만든 뒤 open.longbridge.com 개발자 센터에서 무료로 받아요. Access Token은 기한이 있어서 지나면 새로 넣어야 해요. 홍콩·중국 종목은 앱이 그 종목을 다루게 되면 같은 키로 받아요.", style = appFont(13f), color = Theme.sub)
                Field("App Key", key) { key = it }   // ios: Views/SettingsView.swift:576
                Field("App Secret", secret) { secret = it }   // ios: Views/SettingsView.swift:577
                Field("Access Token", token) { token = it }   // ios: Views/SettingsView.swift:578
                val t = { v: String -> v.trim() }   // ios: Views/SettingsView.swift:579
                val ok = t(key).isNotEmpty() && t(secret).isNotEmpty() && t(token).isNotEmpty()   // ios: Views/SettingsView.swift:580
                // ios: Views/SettingsView.swift:581
                PrimaryButton(title = if (busy) "확인 중…" else "연결하기", color = if (ok) Theme.teal else Theme.muted) {
                    if (!ok || busy) return@PrimaryButton   // ios: Views/SettingsView.swift:582
                    LongbridgeKeys.save(LongbridgeKeys.Keys(key = t(key), secret = t(secret), token = t(token)))   // ios: Views/SettingsView.swift:583
                    secret = ""; token = ""   // ios: Views/SettingsView.swift:584
                    scope.launch { check() }   // ios: Views/SettingsView.swift:585
                }
                msg?.let { T(it, style = appFont(13f), color = Theme.up) }   // ios: Views/SettingsView.swift:587
            }
        }
    }
}

/** 내 키 화면 맨 위 그림: 내 폰 → (토스만) naeilo 서버 → 증권사. 안심은 말보다 그림으로 보인다
 *  (키는 이 폰 보관함에만 두고, 중계 서버는 저장·기록 없이 넘기기만 한다. docs/toss-relay.md) */
@Composable
// ios: Views/SettingsView.swift:661,662,663,664,665
fun TossLinkMap(on: Boolean, server: Boolean = true, broker: String = "Toss증권", what: String = "내 종목의 현재가·일봉·환율을 받아 와요.") {
    // ios: Views/SettingsView.swift:644 .animation(.easeInOut(duration: 0.4), value: on): 색이 0.4초에 바뀐다
    val teal = animateColorAsState(if (on) Theme.teal else Theme.track, tween(400), label = "node").value
    val icon = animateColorAsState(if (on) Color.White else Theme.muted, tween(400), label = "icon").value
    val lockC = animateColorAsState(if (on) Theme.teal else Theme.muted, tween(400), label = "lock").value

    // ios: Views/SettingsView.swift:647,648,662,663
    @Composable
    fun androidx.compose.foundation.layout.RowScope.Node(icn: String, name: String, note: String, lock: Boolean) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ios: Views/SettingsView.swift:649,650,651
            Box(Modifier.size(52.dp, 52.dp).background(teal, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Sym(icn, 22f, icon, scaled = false)
                // ios: Views/SettingsView.swift:652,653,654,655,656
                if (lock) Box(Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(22.dp, 22.dp).background(Theme.card, CircleShape).border(2.dp, lockC, CircleShape), contentAlignment = Alignment.Center) {
                    Sym("lock.fill", 10f, lockC, scaled = false)
                }
            }
            T(name, style = appFont(12f, FontWeight.Bold).copy(textAlign = TextAlign.Center))   // ios: Views/SettingsView.swift:659
            T(note, style = appFont(11f, if (lock) FontWeight.SemiBold else FontWeight.Normal).copy(textAlign = TextAlign.Center), color = if (lock && on) Theme.teal else Theme.muted)   // ios: Views/SettingsView.swift:660
        }
    }

    // ios: Views/SettingsView.swift:623
    Card {
        // ios: Views/SettingsView.swift:624
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Node("iphone", "내 폰", "키가 있는 곳", lock = true)   // ios: Views/SettingsView.swift:670
            TossLinkLine(on = on)   // ios: Views/SettingsView.swift:671
            // ios: Views/SettingsView.swift:672 Alpaca·Longbridge 는 폰에서 바로 받는다
            if (server) {
                Node("server.rack", "naeilo 서버", "이어 주는 곳", lock = false)   // ios: Views/SettingsView.swift:673
                TossLinkLine(on = on)   // ios: Views/SettingsView.swift:674
            }
            Node("chart.line.uptrend.xyaxis", broker, "시세", lock = false)   // ios: Views/SettingsView.swift:676
        }
        // ios: Views/SettingsView.swift:631,632
        T(if (on) "이어졌어요" else "키를 넣으면 이어져요", Modifier.fillMaxWidth(), style = appFont(13f, FontWeight.SemiBold).copy(textAlign = TextAlign.Center), color = if (on) Theme.teal else Theme.muted)
        // ios: Views/SettingsView.swift:636,642
        Row(Modifier.fillMaxWidth().background(Theme.track, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Sym(if (on) "chart.line.uptrend.xyaxis" else "key.fill", 15f, Theme.teal, scaled = false)   // ios: Views/SettingsView.swift:637
            // ios: Views/SettingsView.swift:638,639,640
            T(what, Modifier.weight(1f).padding(end = 0.dp), style = appFont(13f))   // ios: Views/SettingsView.swift:682
        }
    }
}

/** 두 칸 사이 줄: 끊김은 점선 + 빨간 끊긴 표시, 이어짐은 청록 실선 위로 점이 오른쪽에서 왼쪽(내 폰 쪽)으로 흐른다 */
// ios: Views/SettingsView.swift:668,669,673
@Composable
private fun TossLinkLine(on: Boolean) {
    val still = LocalReduceMotion.current   // ios: Views/SettingsView.swift:670
    val teal = Theme.teal; val muted = Theme.muted; val up = Theme.up
    // ios: Views/SettingsView.swift:671,678,679,680 시세가 토스에서 내 폰 쪽으로 온다: 1.4초에 22 → 0 을 되풀이
    val flow = if (on && !still) rememberInfiniteTransition(label = "flow").animateFloat(22f, 0f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x").value else 11f
    // ios: Views/SettingsView.swift:674,686,687
    Canvas(Modifier.size(30.dp, 52.dp).semantics { hideFromAccessibility() }) {
        val mid = size.height / 2
        // ios: Views/SettingsView.swift:675
        if (on) {
            drawLine(teal, Offset(0f, mid), Offset(size.width, mid), 2.dp.toPx())   // ios: Views/SettingsView.swift:676
            drawOval(teal, Offset(flow.dp.toPx(), mid - 4.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()))   // ios: Views/SettingsView.swift:677 (왼쪽 끝 + x)
        } else {   // ios: Views/SettingsView.swift:681
            // ios: Views/SettingsView.swift:690,691 가운데를 지나는 가로줄
            fun line(band: Float): Pair<Offset, Offset> { val y = (size.height - band) / 2 + band / 2; return Offset(0f, y) to Offset(size.width, y) }
            // ios: Views/SettingsView.swift:682 높이 2 칸 가운데에 굵기 2 점선
            val (a, b) = line(2.dp.toPx())
            drawLine(muted.copy(alpha = 0.7f), a, b, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            // ios: Views/SettingsView.swift:683 빨간 끊긴 표시 (2×16, 25도, 왼쪽에서 14)
            rotate(25f, Offset(14.dp.toPx() + 1.dp.toPx(), mid)) { drawRect(up.copy(alpha = 0.9f), Offset(14.dp.toPx(), mid - 8.dp.toPx()), androidx.compose.ui.geometry.Size(2.dp.toPx(), 16.dp.toPx())) }
        }
    }
}

// 앱 아이콘 바꾸기: 기본 · 내일의 별 + 인물 5. 안드로이드는 런처 별칭(activity-alias)을 켜고 끈다
// 인물 아이콘은 그 인물을 만나야 열린다 (세리는 처음부터, 나머지는 인터미션 1~4주차. 쉼터와 같은 단계)
// ios: Views/SettingsView.swift:697,698,712
@Composable
fun AppIconPicker(m: AppModel) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(AppIconPicker.alternateIconName(ctx)) }   // ios: Views/SettingsView.swift:703

    fun open(f: Int?): Boolean = f?.let { m.friendOn(it) } ?: true   // ios: Views/SettingsView.swift:706
    // ios: Views/SettingsView.swift:707,708,709
    fun set(id: String?) {
        if (current == id) return
        AppIconPicker.setAlternateIconName(ctx, id); current = id
    }

    // 시안 조작으로 단계를 되돌려 지금 아이콘이 다시 잠기면 기본 아이콘으로 돌린다
    // ios: Views/SettingsView.swift:739,740
    LaunchedEffect(Unit) { current?.let { c -> AppIconPicker.icons.firstOrNull { it.id == c }?.let { ic -> if (!open(ic.friend)) set(null) } } }
    // ios: Views/SettingsView.swift:713,735,736,737 (3열, 칸 사이 8, 줄 사이 12)
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(18.dp)).border(1.dp, Theme.border, RoundedCornerShape(18.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIconPicker.icons.chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ios: Views/SettingsView.swift:714
                line.forEach { ic ->
                    val on = current == ic.id; val ok = open(ic.friend)   // ios: Views/SettingsView.swift:715
                    // ios: Views/SettingsView.swift:716,727,729,730,731,732
                    Column(
                        Modifier.weight(1f).then(if (ok) Modifier.plainClickable { set(ic.id) } else Modifier.semantics { disabled() })
                            .semantics(mergeDescendants = true) { contentDescription = tr(if (ok) "${ic.name} 아이콘" else "${ic.name} 아이콘, 인터미션 ${ic.friend ?: 0}주차에 열려요"); selected = on }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),   // ios: Views/SettingsView.swift:717
                    ) {
                        // ios: Views/SettingsView.swift:718,719,720,721,722,723
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(15.dp)).border(if (on) 3.dp else 1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.graphics.ImageBitmap.imageResource(com.naeilo.app.ui.drawableId(ctx, ic.prev)), null, Modifier.size(64.dp),
                                colorFilter = if (ok) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }.also { it.timesAssign(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, -0.35f * 255, 0f, 1f, 0f, 0f, -0.35f * 255, 0f, 0f, 1f, 0f, -0.35f * 255, 0f, 0f, 0f, 1f, 0f))) }),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                            if (!ok) Sym("lock.fill", 20f, Color.White, scaled = false)
                        }
                        // ios: Views/SettingsView.swift:724,725
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
    // ios: Views/SettingsView.swift:699
    val icons: List<Icon> = listOf(
        Icon(null, "내일의 별", "iconprev_star", null), Icon("AppIcon-seri", "세리", "iconprev_seri", 0), Icon("AppIcon-sio", "시오", "iconprev_sio", 1),   // ios: Views/SettingsView.swift:700
        Icon("AppIcon-seonbae", "선배", "iconprev_seonbae", 2), Icon("AppIcon-ir", "이르", "iconprev_ir", 3), Icon("AppIcon-sua", "수아", "iconprev_sua", 4),   // ios: Views/SettingsView.swift:701
    )
    // ios: Views/SettingsView.swift:704
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
// ios: Views/SettingsView.swift:746,747
@Composable
fun AppIconScreen(m: AppModel) {
    // ios: Views/SettingsView.swift:748,749,753,755,756
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("앱 아이콘", style = appFont(22f, FontWeight.Bold))   // ios: Views/SettingsView.swift:750
        T("인물 아이콘은 그 인물을 만나면 열려요.", style = appFont(13f), color = Theme.sub)   // ios: Views/SettingsView.swift:751
        AppIconPicker(m)   // ios: Views/SettingsView.swift:752
    }
}

/** 글 복사: 시스템 클립보드에 바로 (LocalClipboardManager 는 더는 안 쓴다) */
@Composable
private fun rememberClip(): (String) -> Unit {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(ctx) { { t: String -> ctx.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("", t)) } }
}
