package com.naeilo.app.ui.support

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.Tab
import com.naeilo.app.data.SubPlan
import com.naeilo.app.data.Support
import com.naeilo.app.data.displayPrice
import com.naeilo.app.data.id
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.settings.FriendSay
import com.naeilo.app.ui.settings.SettingsRoute
import kotlinx.coroutines.launch

/** 이동 막대 제목 (navigationTitle·inline): 설정 화면 길에서 NavPage(SupportTitle) 로 쓴다 */
const val SupportTitle = "구독"   // ios: Views/SupportView.swift:49

/** Google Play 구독 관리 화면 (이 앱 구독만) */
private fun manageUrl(ctx: Context) = "https://play.google.com/store/account/subscriptions?package=" + ctx.packageName

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

// 구독 (월 1,000원 · 연 9,000원). 설정 > 구독, 잠긴 위젯·서재 6장·잠긴 분석에서 연다.
// 구매 복원·구독 관리는 늘 보이고, 가격·기간·자동 갱신 안내와 약관 링크를 같이 둔다.
// ios: Views/SupportView.swift:6,10
@Composable
fun SupportView(m: AppModel) {
    val s = Support.shared   // ios: Views/SupportView.swift:7
    val openURL = LocalUriHandler.current; val ctx = LocalContext.current   // ios: Views/SupportView.swift:8
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (s.products.isEmpty()) s.loadProducts(); s.refresh() }   // ios: Views/SupportView.swift:50
    DisposableEffect(Unit) { onDispose { s.message = null } }   // ios: Views/SupportView.swift:51

    // ios: Views/SupportView.swift:11,48
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        // ios: Views/SupportView.swift:46
        Screen(Modifier.padding(top = 8.dp)) {
            // ios: Views/SupportView.swift:12
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FriendSay(m, key = "sponsor", fallback = "커피 한 잔이면 충분해요.")   // ios: Views/SupportView.swift:13
                // ios: Views/SupportView.swift:14
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("구독", style = appFont(22f, FontWeight.Bold))   // ios: Views/SupportView.swift:15
                    T(lead(s), style = appFont(14f).ls(3f), color = Theme.sub)   // ios: Views/SupportView.swift:16
                    val p = s.plan   // ios: Views/SupportView.swift:17
                    if (p != null) {
                        // ios: Views/SupportView.swift:18,19
                        T("지금 ${p.nameText} 중 · 고마워요", Modifier.background(Theme.gold, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp), style = appFont(13f, FontWeight.Bold), color = Theme.inkFixed)
                    } else if (s.inTrial) {   // ios: Views/SupportView.swift:20
                        // ios: Views/SupportView.swift:21,22
                        T("7일 무료 · ${s.trialLeft}일 남았어요", Modifier.background(Theme.gold, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp), style = appFont(13f, FontWeight.Bold), color = Theme.inkFixed)
                    }
                }
                Opens(s)   // ios: Views/SupportView.swift:25
                SubPlan.entries.forEach { PlanCard(s, it) }   // ios: Views/SupportView.swift:26
                // ios: Views/SupportView.swift:27,29
                if (s.products.isEmpty()) {
                    // Views/SupportView.swift:28 (검사 예외: App Store → Google Play)
                    T("상품을 불러오지 못했어요. Google Play 연결을 확인하거나 잠시 뒤 다시 열어 주세요.", style = appFont(12f), color = Theme.muted)
                }
                // ios: Views/SupportView.swift:31,32
                s.message?.let { msg -> T(msg, style = appFont(14f, FontWeight.Bold), color = Theme.teal) }
                val off = s.busy != null   // ios: Views/SupportView.swift:38
                // ios: Views/SupportView.swift:34,35
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Outline(if (s.busy == "restore") "복원하는 중…" else "구매 복원", off) { scope.launch { s.restore() } }
                    // Views/SupportView.swift:36 (검사 예외: Apple 구독 관리 주소 → Google Play 구독 관리 주소)
                    Outline("구독 관리", off) { runCatching { openURL.openUri(manageUrl(ctx)) } }
                }
                // Views/SupportView.swift:39 (검사 예외: Apple ID·iPhone 설정 안내 → Google 계정·Play 스토어 안내)
                // ios: Views/SupportView.swift:40
                T("Google 계정으로 결제하는 자동 갱신 구독이에요. 기간이 끝나기 전에 끄지 않으면 같은 값으로 다시 결제돼요. 끄기는 위 '구독 관리'(Play 스토어 > 프로필 > 결제 및 정기 결제 > 정기 결제)에서 할 수 있어요. 구독을 멈춰도 이미 연 캐릭터 상품과 받은 이야기는 그대로예요.",
                    style = appFont(12f).ls(3f), color = Theme.muted)
                // Views/SupportView.swift:41 (검사 예외: Apple 표준 EULA → Google Play 이용약관)
                // ios: Views/SupportView.swift:42
                T("이용약관 (Google Play 이용약관)", Modifier.plainClickable { runCatching { openURL.openUri("https://play.google.com/about/play-terms/") } }, style = appFont(12f, FontWeight.Bold), color = Theme.teal)
                // ios: Views/SupportView.swift:43,44
                T("개인정보처리방침", Modifier.plainClickable { runCatching { openURL.openUri("https://naeilo.com/web/privacy.html") } }, style = appFont(12f, FontWeight.Bold), color = Theme.teal)
            }
        }
    }
}

// ios: Views/SupportView.swift:54
private fun lead(s: Support): String {
    if (s.plan != null) return "구독 덕분에 naeilo를 계속 만들어요. 위젯, 목표와 확률 전망, 서재 외전이 모두 열려 있어요."   // ios: Views/SupportView.swift:55
    if (s.inTrial) return "처음 7일은 서재 빼고 모두 무료예요. 7일 뒤에도 위젯과 목표·확률 전망을 보려면 구독해 주세요."   // ios: Views/SupportView.swift:56
    return "7일 무료가 끝났어요. 홈 숫자·분석·게임과 이미 연 캐릭터는 그대로 쓸 수 있고, 위젯과 목표·확률 전망은 구독하면 다시 열려요."   // ios: Views/SupportView.swift:57
}

/** 구독하면 열리는 것 */
// ios: Views/SupportView.swift:61
@Composable
private fun Opens(s: Support) {
    // ios: Views/SupportView.swift:62
    Card {
        T("구독하면", style = appFont(15f, FontWeight.Bold))   // ios: Views/SupportView.swift:63
        // ios: Views/SupportView.swift:64
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ios: Views/SupportView.swift:65,66
            listOf("위젯 전부 (기본·인물·서재·잠금 화면)", "목표(내 길)와 확률 전망", "서재 외전 『이종 공명』 6장부터 끝까지 (앱 연재판)",
                "앱 연재판 1권: 연 구독, 또는 월 구독 ${Support.vol1Months}개월 이상").forEach { a ->
                // ios: Views/SupportView.swift:67
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("·", Modifier.alignByBaseline(), style = appFont(14f, FontWeight.Bold))   // ios: Views/SupportView.swift:68
                    T(a, Modifier.alignByBaseline(), style = appFont(14f))   // ios: Views/SupportView.swift:69
                }
            }
            // ios: Views/SupportView.swift:72
            if (s.plan == SubPlan.monthly && !s.vol1Open) {
                T("1권까지 월 구독 ${Support.vol1Months - s.monthsPaid}번 남았어요.", style = appFont(13f), color = Theme.sub)   // ios: Views/SupportView.swift:73
            }
        }
    }
}

// ios: Views/SupportView.swift:79
@Composable
private fun PlanCard(s: Support, plan: SubPlan) {
    val on = s.plan == plan; val p = s.product(plan)   // ios: Views/SupportView.swift:80
    val act = LocalContext.current.activity(); val scope = rememberCoroutineScope()
    // ios: Views/SupportView.swift:81
    Card(stroke = if (on) Theme.teal else Theme.border) {
        // ios: Views/SupportView.swift:82
        Row(Modifier.fillMaxWidth()) {
            T(plan.nameText, Modifier.alignByBaseline(), style = appFont(17f, FontWeight.Bold))   // ios: Views/SupportView.swift:83
            Spacer(Modifier.weight(1f))   // ios: Views/SupportView.swift:84
            // ios: Views/SupportView.swift:85,86
            T(p?.let { "${it.displayPrice} / ${plan.unit}" } ?: (if (plan == SubPlan.monthly) "1,000원 / 한 달" else "9,000원 / 1년"),
                Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold), color = Theme.sub)
        }
        if (plan == SubPlan.yearly) T("월 구독보다 25% 싸고, 1권이 바로 열려요.", style = appFont(13f), color = Theme.sub)   // ios: Views/SupportView.swift:88
        val off = on || p == null || s.busy != null   // ios: Views/SupportView.swift:95
        // ios: Views/SupportView.swift:89,92,93
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(if (on) Theme.mintBg else Theme.teal, RoundedCornerShape(12.dp))
            .then(if (off) Modifier else Modifier.plainClickable { if (act != null) scope.launch { s.buy(act, p) } }), contentAlignment = Alignment.Center) {
            // ios: Views/SupportView.swift:90,91
            T(if (on) "구독 중이에요" else if (p == null) "지금은 구독할 수 없어요" else if (s.busy == p.id) "결제하는 중…" else "${plan.nameText}하기",
                style = appFont(15f, FontWeight.Bold), color = if (on) Theme.teal else Color.White)
        }
    }
}

// ios: Views/SupportView.swift:99
@Composable
private fun RowScope.Outline(t: String, off: Boolean, go: () -> Unit) {
    // ios: Views/SupportView.swift:100,102,103,105
    Box(Modifier.weight(1f).defaultMinSize(minHeight = 44.dp).border(1.5.dp, Theme.teal, RoundedCornerShape(12.dp))
        .then(if (off) Modifier else Modifier.plainClickable(go)), contentAlignment = Alignment.Center) {
        T(t, style = appFont(15f, FontWeight.Bold), color = Theme.teal)   // ios: Views/SupportView.swift:101
    }
}

/** 7일 무료가 끝나 잠긴 화면 (분석 › 내 길·확률 전망). 누르면 설정 > 구독으로 */
@Composable
fun SubLocked(m: AppModel, what: String) {   // ios: Views/SupportView.swift:110,111,112,113
    // ios: Views/SupportView.swift:114,126,127,128
    Column(Modifier.fillMaxSize().background(Theme.bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Sym("lock.fill", 28f, Theme.teal)   // ios: Views/SupportView.swift:115
        T("7일 무료가 끝났어요", style = appFont(20f, FontWeight.Bold))   // ios: Views/SupportView.swift:116
        // ios: Views/SupportView.swift:117,118
        T("${what}은 구독하면 다시 볼 수 있어요.\n월 1,000원 · 연 9,000원", style = appFont(14f).ls(3f).copy(textAlign = TextAlign.Center), color = Theme.sub)
        // ios: Views/SupportView.swift:119,120,121,122,124
        Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(Theme.teal, RoundedCornerShape(12.dp))
            .plainClickable { m.tab = Tab.settings; m.settingsPath = listOf(SettingsRoute.support) }, contentAlignment = Alignment.Center) {
            T("구독 보기", style = appFont(15f, FontWeight.Bold), color = Color.White)
        }
    }
}
