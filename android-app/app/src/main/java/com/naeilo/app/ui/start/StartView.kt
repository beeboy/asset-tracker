package com.naeilo.app.ui.start

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naeilo.app.AppModel
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.eta
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.data.Lines
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.PrimaryButton
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.pulse
import com.naeilo.app.ui.settings.SyncView
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.EaseOut
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 첫 질문 (시안 첫 화면): 지금 내 투자, 플러스인가요 마이너스인가요?
// ios: Views/StartView.swift:4,5,9
@Composable
fun StartView(m: AppModel, modifier: Modifier = Modifier) {
    var pick by remember { mutableStateOf<Route?>(null) }     // ios: Views/StartView.swift:6 고른 길: 아래 예보 카드 숫자가 그 길의 숫자로 바뀐다
    var restore by remember { mutableStateOf(false) }        // ios: Views/StartView.swift:7 다른 기기 값 불러오기 (기기 동기화)

    // ios: Views/StartView.swift:50,51
    @Composable
    fun Option(t: String, sub: String, r: Route) {
        val on = pick == r
        // ios: Views/StartView.swift:52,57,58,59,60,62,63 withAnimation(.easeOut(duration: 0.2)): 테두리·카드 숫자가 0.2초에 바뀐다
        Column(
            Modifier.fillMaxWidth().plainClickable { pick = if (on) null else r }
                .semantics(mergeDescendants = true) { selected = on }
                .background(Theme.card, RoundedCornerShape(18.dp))
                .border(2.dp, androidx.compose.animation.animateColorAsState(if (on) Theme.teal else Theme.border, tween(200, easing = EaseOut), label = "opt").value, RoundedCornerShape(18.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),   // ios: Views/StartView.swift:53
        ) {
            T(t, style = appFont(19f, FontWeight.Bold))   // ios: Views/StartView.swift:54
            T(sub, style = appFont(14f), color = Theme.sub)   // ios: Views/StartView.swift:55
        }
    }

    // 불러오기가 끝나면 첫 질문은 닫힌다
    LaunchedEffect(m.onboarded) { if (m.onboarded) restore = false }   // ios: Views/StartView.swift:47
    // ios: Views/StartView.swift:10,44
    Fg(Theme.ink) {   // ios: Views/StartView.swift:42
        // ios: Views/StartView.swift:11,41
        Column(modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            T("naeilo", Modifier.padding(top = 24.dp), style = appFont(20f, FontWeight.Bold))   // ios: Views/StartView.swift:12
            // ios: Views/StartView.swift:13
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelN("spr_seri", width = 56.dp, height = 80.dp, modifier = Modifier.charIdle().semantics { contentDescription = "세리" })   // ios: Views/StartView.swift:14
                // ios: Views/StartView.swift:15,16,17,18,19,20
                T(Lines.pick("start.seri") ?: "처음 오셨군요. …… 하나만 물어볼게요.",
                  Modifier.padding(bottom = 14.dp)
                      .background(Theme.card, RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp))
                      .border(1.dp, Theme.border, RoundedCornerShape(topStart = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp, topEnd = 14.dp))
                      .padding(horizontal = 14.dp, vertical = 10.dp),
                  style = appFont(15f))
            }
            T("지금 내 투자,\n플러스? 마이너스?", style = appFont(30f, FontWeight.Bold).ls(4f))   // ios: Views/StartView.swift:22
            // ios: Views/StartView.swift:23
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Option("마이너스예요", "본전까지 가는 길을 같이 찾아요", Route.recover)   // ios: Views/StartView.swift:24
                Option("플러스예요", "목표까지 가는 길", Route.plus)   // ios: Views/StartView.swift:25
                Option("아직 시작 전이에요", "매달 얼마면 언제 얼마?", Route.novice)   // ios: Views/StartView.swift:26
            }
            // ios: Views/StartView.swift:28,29
            pick?.let { p -> PrimaryButton(title = "시작하기") { m.startRoute(p) } }
            T("잘 모르겠다면 \"마이너스\"로 시작하세요. 매수 단가를 넣으면 자동으로 알려드려요.", style = appFont(13f), color = Theme.muted)   // ios: Views/StartView.swift:31
            // ios: Views/StartView.swift:32,33,34,36 (Label + systemImage)
            Row(Modifier.fillMaxWidth().plainClickable { restore = true }.defaultMinSize(minHeight = 44.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Sym("arrow.triangle.2.circlepath", 14f, Theme.teal)
                Spacer(Modifier.padding(start = 6.dp))
                T("다른 폰 값 불러오기", style = appFont(14f, FontWeight.SemiBold), color = Theme.teal)
            }
            StartForecastCard(route = pick, modifier = Modifier.padding(top = 8.dp))   // ios: Views/StartView.swift:37
            // ios: Views/StartView.swift:38,39
            T("See Tomorrow, Today.", Modifier.fillMaxWidth().padding(top = 12.dp), style = appFont(12f).copy(letterSpacing = 0.4.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Theme.muted)
        }
    }
    // ios: Views/StartView.swift:45 (NavigationStack 안의 SyncView)
    Sheet(restore, onDismiss = { restore = false }) { SyncView(m) }
}

// 첫 질문 아래 예보 카드 (캐릭터 설정 스레드 시안 first-question-forecast). 숫자는 예시 보유로 계산한 값이다.
// 고른 길에 따라: 마이너스 → 본전까지 보통 걸리는 기간, 플러스(고르기 전 기본) → 3년 안에 목표에 닿을 확률, 시작 전 → 매달 넣으면 3년 뒤 보통
object StartForecast {
    // 예시 가정: 연 기대 8%, 흔들림 12% (주식과 채권을 섞은 예시 보유)
    const val mu = 0.08; const val sigma = 0.12   // ios: Views/StartView.swift:73
    val g: Double get() = mu - sigma * sigma / 2   // ios: Views/StartView.swift:74

    // ios: Views/StartView.swift:76
    class Ex(val v0: Double, val monthly: Double, val line: Double?, val lineName: String, val big: String, val sub: String)
    // ios: Views/StartView.swift:77
    fun ex(route: Route?): Ex {
        val g = g; val s = sigma   // ios: Views/StartView.swift:78
        // ios: Views/StartView.swift:79
        return when (route) {
            // ios: Views/StartView.swift:80
            Route.recover -> {
                // 4,350만원 → 본전 5,000만원: 보통의 경우(중앙값)가 본전에 닿는 때 = ln(본전/지금) / g
                val v0 = 4350.0; val c = 5000.0; val mo = ln(c / v0) / g * 12   // ios: Views/StartView.swift:82
                Ex(v0 = v0, monthly = 0.0, line = c, lineName = "본전", big = AppModel.eta(mo).replace(" 뒤", "").replace("뒤", ""), sub = "본전까지 보통 걸리는 기간")   // ios: Views/StartView.swift:83
            }
            // ios: Views/StartView.swift:84
            Route.novice -> {
                val m = 50.0; val v = value(0.0, m, 3.0, 0.0)   // ios: Views/StartView.swift:85
                Ex(v0 = 0.0, monthly = m, line = m * 36, lineName = "넣은 돈", big = AppModel.wonK(v), sub = "월 50만 · 3년 뒤 보통")   // ios: Views/StartView.swift:86
            }
            // ios: Views/StartView.swift:87
            else -> {
                // 5,000만원 → 목표 5,800만원, 3년 뒤 목표 이상일 확률
                val v0 = 5000.0; val k = 5800.0   // ios: Views/StartView.swift:89
                val p = AppModel.normCDF((ln(v0 / k) + g * 3) / (s * sqrt(3.0)))   // ios: Views/StartView.swift:90
                Ex(v0 = v0, monthly = 0.0, line = k, lineName = "목표", big = AppModel.pct(p), sub = "3년 안 목표 확률")   // ios: Views/StartView.swift:91
            }
        }
    }

    /** t년 뒤 값 (z = 표준정규 분위). 매달 넣는 돈은 평균 절반 기간만 불어난다고 본다 */
    // ios: Views/StartView.swift:96,97
    fun value(v0: Double, m: Double, t: Double, z: Double): Double =
        v0 * exp(g * t + z * sigma * sqrt(t)) + m * 12 * t * exp(g * t / 2 + z * sigma * sqrt(t / 3))

    // 3년 부채꼴: 5~95 띠, 25~75 띠, 보통의 경우 선, 노란 점선(목표·본전·넣은 돈)
    /** 부채꼴 시작점 (박동 원 자리). fan 의 X·Y 와 같은 셈 */
    // ios: Views/StartView.swift:129
    fun fanStart(e: Ex, s: Size, dp: Float): Offset { val p = fanStartD(e, s.width.toDouble(), s.height.toDouble(), dp.toDouble()); return Offset(p.first.toFloat(), p.second.toFloat()) }
    /** 같은 셈을 Double 로 (그림 자리는 Float 라 끝에서만 바꾼다. 숫자 맞추기 E 가 이 값을 Swift 와 비교한다) */
    fun fanStartD(e: Ex, w: Double, h: Double, dp: Double): Pair<Double, Double> {
        val top = max(value(e.v0, e.monthly, 3.0, 1.645), (e.line ?: 0.0)) * 1.05   // ios: Views/StartView.swift:130
        val bot = if (e.v0 > 0) min(value(e.v0, e.monthly, 3.0, -1.645), e.v0) * 0.97 else 0.0   // ios: Views/StartView.swift:131
        val v = value(e.v0, e.monthly, 0.0, 0.0)   // ios: Views/StartView.swift:132
        return Pair(6 * dp, h - 6 * dp - ((v - bot) / (top - bot)) * (h - 12 * dp))   // ios: Views/StartView.swift:133
    }
}

// ios: Views/StartView.swift:69,70,100
@Composable
fun StartForecastCard(route: Route?, modifier: Modifier = Modifier) {
    // ios: Views/StartView.swift:123 .animation(.easeOut(duration: 0.25), value: route): 길을 바꾸면 0.25초에 넘어간다
    AnimatedContent(route, modifier, transitionSpec = { fadeIn(tween(250, easing = EaseOut)) togetherWith fadeOut(tween(250, easing = EaseOut)) }, label = "fc") { r ->
        val e = StartForecast.ex(r)   // ios: Views/StartView.swift:101
        // ios: Views/StartView.swift:102,120,121,122,124
        Column(
            Modifier.fillMaxWidth().background(hexD(0x141A2B, 0x161C2E), RoundedCornerShape(20.dp)).semantics(mergeDescendants = true) {}.animateContentSize(tween(250, easing = EaseOut)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T("naeilo · 3년 뒤가 보여요", style = appFont(13f, FontWeight.SemiBold), color = Theme.yellow)   // ios: Views/StartView.swift:103
            // ios: Views/StartView.swift:104,108
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(e.big, Modifier.alignByBaseline(), style = appFont(40f, FontWeight.Normal), maxLines = 1, color = Color.White)   // ios: Views/StartView.swift:105
                T(e.sub, Modifier.alignByBaseline(), style = appFont(14f), color = hex(0xC9CFDB))   // ios: Views/StartView.swift:106
            }
            Fan(e)   // ios: Views/StartView.swift:109 (높이 140)
            // ios: Views/StartView.swift:110,111
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("지금", "1년", "2년", "3년").forEach { t ->
                    T(t, style = appFont(12f), color = hex(0x9AA5B8))   // ios: Views/StartView.swift:112
                    if (t != "3년") Spacer(Modifier.weight(1f))   // ios: Views/StartView.swift:113
                }
            }
            // ios: Views/StartView.swift:116,117
            T("수량만 넣으면 내 숫자로", style = appFont(14f).ls(3f), color = hex(0xEEF0F7))
            T("예시 · 확률 모형 · 투자 권유 아님", style = appFont(11f), color = hex(0x8A93A6))   // ios: Views/StartView.swift:118
        }
    }
}

// ios: Views/StartView.swift:136
@Composable
private fun Fan(e: StartForecast.Ex) {
    val yellow = Theme.yellow
    val tm = rememberTextMeasurer()
    val label = tm.measure(e.lineName, TextStyle(fontSize = 12.sp, color = yellow))   // ios: Views/StartView.swift:161 .font(.system(size: 12)): 큰 글자 설정을 따르지 않는다
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    // ios: Views/StartView.swift:137,166,167,109
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "3년 예상 범위 부채꼴" }
        .pulse(Color.White, dot = 10.dp) { s -> StartForecast.fanStart(e, s, density) }) {
        val W = size.width; val H = size.height   // ios: Views/StartView.swift:138
        val ts = (0..30).map { if (it == 30) 3.0 else it * 0.1 }   // ios: Views/StartView.swift:139 (stride through 는 끝값 3 을 그대로 넣는다)
        val top = max(StartForecast.value(e.v0, e.monthly, 3.0, 1.645), (e.line ?: 0.0)) * 1.05   // ios: Views/StartView.swift:140
        val bot = if (e.v0 > 0) min(StartForecast.value(e.v0, e.monthly, 3.0, -1.645), e.v0) * 0.97 else 0.0   // ios: Views/StartView.swift:141
        val X = { t: Double -> 6.dp.toPx() + (t / 3).toFloat() * (W - 12.dp.toPx()) }   // ios: Views/StartView.swift:142
        val Y = { v: Double -> H - 6.dp.toPx() - ((v - bot) / (top - bot)).toFloat() * (H - 12.dp.toPx()) }   // ios: Views/StartView.swift:143
        // ios: Views/StartView.swift:144,145
        fun band(z: Double): Path {
            val p = Path()
            ts.forEachIndexed { i, t -> val x = X(t); val y = Y(StartForecast.value(e.v0, e.monthly, t, z)); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }   // ios: Views/StartView.swift:146
            ts.reversed().forEach { t -> p.lineTo(X(t), Y(StartForecast.value(e.v0, e.monthly, t, -z))) }   // ios: Views/StartView.swift:147
            p.close(); return p   // ios: Views/StartView.swift:148
        }
        drawPath(band(1.645), hex(0x2A3A6E).copy(alpha = 0.75f))   // ios: Views/StartView.swift:150
        drawPath(band(0.674), hex(0x3A5296).copy(alpha = 0.85f))   // ios: Views/StartView.swift:151
        val med = Path()   // ios: Views/StartView.swift:152
        ts.forEachIndexed { i, t -> val x = X(t); val y = Y(StartForecast.value(e.v0, e.monthly, t, 0.0)); if (i == 0) med.moveTo(x, y) else med.lineTo(x, y) }   // ios: Views/StartView.swift:153
        drawPath(med, hex(0x8FB0FF), style = Stroke(2.5.dp.toPx()))   // ios: Views/StartView.swift:154
        // ios: Views/StartView.swift:155
        e.line?.let { l ->
            // 목표·본전은 가로선, 넣은 돈은 매달 쌓이므로 비스듬한 선
            val y1 = if (e.monthly > 0) Y(0.0) else Y(l)   // ios: Views/StartView.swift:157
            // ios: Views/StartView.swift:158,159
            drawLine(yellow, Offset(X(0.0), y1), Offset(if (e.monthly > 0) X(3.0) else W, Y(l)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            val lp = if (e.monthly > 0) Offset(X(2.2), Y(l * 2.2 / 3) + 14.dp.toPx()) else Offset(16.dp.toPx(), Y(l) - 10.dp.toPx())   // ios: Views/StartView.swift:160
            drawText(label, topLeft = Offset(lp.x, lp.y - label.size.height / 2f))   // ios: Views/StartView.swift:161 (왼쪽 가운데 기준, 12)
        }
        val o = Offset(X(0.0), Y(StartForecast.value(e.v0, e.monthly, 0.0, 0.0)))   // ios: Views/StartView.swift:163
        drawOval(Color.White, Offset(o.x - 5.dp.toPx(), o.y - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()))   // ios: Views/StartView.swift:164
    }
}
