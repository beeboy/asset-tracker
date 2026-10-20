package com.naeilo.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.naeilo.app.AppModel
import com.naeilo.app.core.Store
import com.naeilo.app.core.WChar
import com.naeilo.app.core.WReward
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.friendName
import com.naeilo.app.pending.BoardCharView
import com.naeilo.app.pending.CharBG
import com.naeilo.app.pending.CharEntry
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.pending.LocalCharDark
import com.naeilo.app.pending.LocalCharHide
import com.naeilo.app.pending.GardenCharView
import com.naeilo.app.pending.HeightCharView
import com.naeilo.app.pending.HillCharView
import com.naeilo.app.pending.LadderCharView
import com.naeilo.app.pending.PuzzleCharView
import com.naeilo.app.pending.TowerCharView
import com.naeilo.app.pending.TugCharView
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 미션 위젯 공유 (확정 시안): 인물 위젯 8개 중 하나와 위젯 모양(라이트·다크·투명·틴트)을 골라 이미지로 보낸다.
// 기본은 꽃밭, 모양은 폰 라이트/다크 설정, 금액은 숨김. 종목 이름은 위젯에 원래 없다.
// ios: Views/ShareCard.swift:5,9
@Composable
fun ShareCardButton(m: AppModel,
    title: String = "미션 위젯 공유",   // ios: Views/ShareCard.swift:6
    plain: Boolean = false,   // ios: Views/ShareCard.swift:7 흰 바탕 · 회색 테두리 (미션 탭 보조 버튼)
) {
    var show by remember { mutableStateOf(false) }   // ios: Views/ShareCard.swift:8
    // ios: Views/ShareCard.swift:10,11,12,13,14,15,17 (Label + systemImage)
    Row(
        Modifier.fillMaxWidth().plainClickable { show = true }.defaultMinSize(minHeight = 44.dp)
            .background(if (plain) Theme.card else Color.Transparent, RoundedCornerShape(12.dp))
            .border(1.5.dp, if (plain) Theme.border else Theme.teal, RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (plain) Theme.ink else Theme.teal
        Sym("square.and.arrow.up", 14f, fg); Spacer(Modifier.width(6.dp))
        FitText(title, appFont(14f, FontWeight.Bold), 0.8f, maxLines = 1, color = fg)
    }
    Sheet(show, onDismiss = { show = false }) { ShareCardSheet(m, dismiss = { show = false }) }   // ios: Views/ShareCard.swift:18
}

/** 위젯 8개: 고르기 칸 이름·아이콘, 인물 한마디 */
// ios: Views/ShareCard.swift:25
private class W(val name: String, val icon: String, val say: String)

// ios: Views/ShareCard.swift:22
object ShareCard {
    // ios: Views/ShareCard.swift:26
    internal val list: List<Any> = listOf(
        W(name = "언덕", icon = "chart.line.uptrend.xyaxis", say = "언덕이 조금 높아졌네."),   // ios: Views/ShareCard.swift:27
        W(name = "사다리", icon = "stairs", say = "한 칸 더 올라왔어."),   // ios: Views/ShareCard.swift:28
        W(name = "블록 탑", icon = "square.stack.3d.up", say = "한 층씩 쌓고 있어."),   // ios: Views/ShareCard.swift:29
        W(name = "전광판", icon = "tv", say = "오늘도 불 켜졌어."),   // ios: Views/ShareCard.swift:30
        W(name = "키 재기", icon = "ruler", say = "어제보다 조금 컸네."),   // ios: Views/ShareCard.swift:31
        W(name = "퍼즐", icon = "puzzlepiece", say = "조각 하나 더 맞췄어."),   // ios: Views/ShareCard.swift:32
        W(name = "꽃밭", icon = "camera.macro", say = "다 피면 다음 밭이야."),   // ios: Views/ShareCard.swift:33
        W(name = "줄다리기", icon = "arrow.left.and.right", say = "오늘은 우리 편이 당겼어."),   // ios: Views/ShareCard.swift:34
        W(name = "다 같이", icon = "person.3.fill", say = "우리 다 같이 축하해! 목표 칸을 다 깼어."),   // ios: Views/ShareCard.swift:35 8: 100칸 벽돌을 다 깬 날만
    )
    val modes = listOf("라이트", "다크", "투명", "틴트")   // ios: Views/ShareCard.swift:37
}
@Suppress("UNCHECKED_CAST") private val list: List<W> get() = ShareCard.list as List<W>

/** 카드 색: 라이트는 흰 카드, 다크는 어두운 카드, 투명은 배경화면 같은 하늘색 그라데이션(유리 위젯이 비쳐 보이게), 틴트는 깊은 청록 그라데이션 */
// ios: Views/ShareCard.swift:107
private class Look(val bg: List<Long>, val stroke: Color, val fg: Color, val sub: Color, val bubble: Color, val name: Color)

/** 말풍선 꼬리: 오른쪽 변이 풍선에 붙고 왼쪽 끝이 뾰족하다 */
// ios: Views/ShareCard.swift:209,210,211
val BubbleTail = GenericShape { r, _ ->
    moveTo(r.width, 0f)   // ios: Views/ShareCard.swift:212
    lineTo(r.width, r.height)   // ios: Views/ShareCard.swift:213
    lineTo(0f, r.height - r.height / 4)   // ios: Views/ShareCard.swift:214
    close()   // ios: Views/ShareCard.swift:215,216
}

/** 말풍선: 왼쪽 아래 꼬리가 인물 쪽을 가리킨다 */
// ios: Views/ShareCard.swift:221,222
val BubbleShape = GenericShape { r, _ ->
    val k = density
    addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset.Zero, r), androidx.compose.ui.geometry.CornerRadius(12 * k)))   // ios: Views/ShareCard.swift:223
    moveTo(6 * k, r.height - 14 * k)   // ios: Views/ShareCard.swift:224
    lineTo(-7 * k, r.height - 4 * k)   // ios: Views/ShareCard.swift:225
    lineTo(12 * k, r.height - 6 * k)   // ios: Views/ShareCard.swift:226
    close()   // ios: Views/ShareCard.swift:227,228
}
private val density: Float get() = android.content.res.Resources.getSystem().displayMetrics.density

/** 공유 창: 버튼을 누른 뒤에만 뜬다 (UIActivityViewController 자리 = 안드로이드 공유 고르기) */
// ios: Views/ShareCard.swift:235,236
object ActivitySheet {
    // ios: Views/ShareCard.swift:237,238
    fun open(ctx: Context, url: Uri) {
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, url).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ios: Views/ShareCard.swift:22,23,45,46,50
@Composable
fun ShareCardSheet(m: AppModel, party: Boolean = false, dismiss: () -> Unit) {   // 100칸 벽돌을 다 깬 뒤: '다 같이'(여섯 모두)가 보이고 기본으로 골라진다
    var picked by remember { mutableStateOf(6) }          // ios: Views/ShareCard.swift:39 기본 꽃밭
    var mode by remember { mutableStateOf<Int?>(null) }   // ios: Views/ShareCard.swift:40 null: 폰 설정 따라감
    var showAmount by remember { mutableStateOf("hide") }   // ios: Views/ShareCard.swift:41
    var myMsg by remember { mutableStateOf("") }          // ios: Views/ShareCard.swift:42 비우면 인물 기본 대사
    var shareURL by remember { mutableStateOf<Uri?>(null) }   // ios: Views/ShareCard.swift:43 버튼 누를 때만 그린 이미지
    val scheme = LocalPal.current.isDark   // ios: Views/ShareCard.swift:44
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    val curMode = mode ?: (if (scheme) 1 else 0)   // ios: Views/ShareCard.swift:48

    // ios: Views/ShareCard.swift:108,109
    val look = when (curMode) {
        0 -> Look(bg = listOf(0xFFFFFF), stroke = hex(0xE3E7EC), fg = hex(0x15202B), sub = hex(0x5D6673), bubble = hex(0xF0F2F5), name = hex(0x0B6B66))   // ios: Views/ShareCard.swift:110
        2 -> Look(bg = listOf(0x3A6EA5, 0x7A6BBF, 0xD98FA8), stroke = Color.Transparent, fg = Color.White, sub = Color.White.copy(alpha = 0.8f), bubble = Color.White.copy(alpha = 0.2f), name = Color.White)   // ios: Views/ShareCard.swift:111
        3 -> Look(bg = listOf(0x0C2622, 0x1F4A42), stroke = Color.Transparent, fg = hex(0xF2F4F7), sub = hex(0x9CC9BF), bubble = hex(0x1E3A35), name = Theme.mint)   // ios: Views/ShareCard.swift:112
        else -> Look(bg = listOf(0x10151F), stroke = hex(0x222A37), fg = hex(0xF2F4F7), sub = hex(0xAAB3C2), bubble = hex(0x1D2430), name = Theme.mint)   // ios: Views/ShareCard.swift:113
    }

    /** 실제 인물 위젯을 고른 모양으로 그린다 (앱 '인물 위젯' 미리보기와 같은 크기 158) */
    // ios: Views/ShareCard.swift:149
    @Composable
    fun Widget(f: String) {
        val e = CharEntry(r = Store.read(WReward.serializer(), "reward.json") ?: WReward.sample, c = WChar.of(f), locked = false, preview = true)   // ios: Views/ShareCard.swift:150
        val dark = curMode != 0   // ios: Views/ShareCard.swift:151
        // ios: Views/ShareCard.swift:152,164,165,166,167
        @Composable fun content() = Box(Modifier.size(158.dp, 158.dp).padding(16.dp)) {
            CompositionLocalProvider(LocalCharDark provides dark, LocalCharHide provides (showAmount == "hide")) { Fg(Color.White) {
                // ios: Views/ShareCard.swift:153
                when (picked) {
                    0 -> HillCharView(e = e)   // ios: Views/ShareCard.swift:154
                    1 -> LadderCharView(e = e)   // ios: Views/ShareCard.swift:155
                    2 -> TowerCharView(e = e)   // ios: Views/ShareCard.swift:156
                    3 -> BoardCharView(e = e)   // ios: Views/ShareCard.swift:157
                    4 -> HeightCharView(e = e)   // ios: Views/ShareCard.swift:158
                    5 -> PuzzleCharView(e = e)   // ios: Views/ShareCard.swift:159
                    6 -> GardenCharView(e = e)   // ios: Views/ShareCard.swift:160
                    else -> TugCharView(e = e)   // ios: Views/ShareCard.swift:161
                }
            } }
        }
        val mint = Theme.mint
        // ios: Views/ShareCard.swift:168,176,177,178
        Box(Modifier.size(158.dp, 158.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, if (curMode == 2) Color.White.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(22.dp))) {
            // ios: Views/ShareCard.swift:169
            when (curMode) {
                0, 1 -> CharBG(c = e.c, locked = false, dark = dark)   // ios: Views/ShareCard.swift:170
                2 -> Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.10f)))   // ios: Views/ShareCard.swift:171
                else -> Box(Modifier.fillMaxSize().background(hex(0x15201E)))   // ios: Views/ShareCard.swift:172
            }
            // 틴트: 흑백으로 바꾼 뒤 민트를 곱한다 (grayscale(1).colorMultiply)
            // ios: Views/ShareCard.swift:174
            if (curMode == 3) Box(Modifier.drawWithContent {
                val g = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
                val cm = ColorMatrix(floatArrayOf(
                    g[0] * mint.red, g[1] * mint.red, g[2] * mint.red, 0f, 0f,
                    g[0] * mint.green, g[1] * mint.green, g[2] * mint.green, 0f, 0f,
                    g[0] * mint.blue, g[1] * mint.blue, g[2] * mint.blue, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f))
                drawIntoCanvas { c -> c.saveLayer(Rect(Offset.Zero, size), Paint().apply { colorFilter = ColorFilter.colorMatrix(cm) }); drawContent(); c.restore() }
            }) { content() } else content()
        }
    }

    /** 스페셜: 친구 다섯 + 나, 여섯이 한 줄로 서고 아래 말풍선 */
    // ios: Views/ShareCard.swift:182
    @Composable
    fun AllFriends(w: W, l: Look) {
        // ios: Views/ShareCard.swift:183
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ios: Views/ShareCard.swift:184,185,187
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                (Shelter.friends.map { it.id } + listOf("me")).forEach { CharPixel(id = it, width = 48.dp, height = 69.dp) }
            }
            // ios: Views/ShareCard.swift:188,189,190,191,192
            T(if (myMsg.trim().isEmpty()) w.say else myMsg, Modifier.fillMaxWidth().background(l.bubble, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
              style = TextStyle(fontSize = 14.sp), color = l.fg)
        }
    }

    // 공유 이미지 카드: 고른 위젯 모양에 맞춰 바탕색이 바뀐다
    // ios: Views/ShareCard.swift:118
    @Composable
    fun Card0() {
        val w = list[picked]; val f = m.homeFriendShown; val l = look   // ios: Views/ShareCard.swift:119
        // ios: Views/ShareCard.swift:145 (위젯 모양과 상관없이 어두운 색 벌)
        CompositionLocalProvider(LocalPal provides Palette.Dark) {
            // ios: Views/ShareCard.swift:120,140,141,142,143,144
            Column(
                Modifier.width(380.dp)   // 와이드 카드, 사방 여백 20
                    .background(Brush.linearGradient(l.bg.map { hex(it) }.let { if (it.size == 1) it + it else it }, Offset.Zero, Offset.Infinite), RoundedCornerShape(28.dp))
                    .border(1.dp, l.stroke, RoundedCornerShape(28.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ios: Views/ShareCard.swift:121
                if (picked == 8) AllFriends(w, l) else {
                    // ios: Views/ShareCard.swift:122
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Widget(f)   // ios: Views/ShareCard.swift:123
                        // 말풍선: 왼쪽 꼬리가 위젯 속 인물을 가리킨다
                        Box(Modifier.weight(1f)) {
                            // ios: Views/ShareCard.swift:125,129,130,131
                            Column(Modifier.fillMaxWidth().background(l.bubble, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // 풍선이 오른쪽 여백 20 까지 찬다
                                T(m.friendName(f), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = l.name)   // ios: Views/ShareCard.swift:126
                                T(if (myMsg.trim().isEmpty()) w.say else myMsg, style = TextStyle(fontSize = 14.sp), color = l.fg)   // ios: Views/ShareCard.swift:127
                            }
                            // ios: Views/ShareCard.swift:132,133
                            Box(Modifier.align(Alignment.BottomStart).offset(x = (-9).dp, y = (-18).dp).size(10.dp, 16.dp).background(l.bubble, BubbleTail))
                        }
                    }
                }
                // ios: Views/ShareCard.swift:137,138
                T("naeilo", Modifier.fillMaxWidth(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End), color = l.fg)
            }
        }
    }

    /** 저장 파일 이름: 날짜-위젯명-naeilo.png */
    // ios: Views/ShareCard.swift:197
    suspend fun rendered(): Uri? {
        // ios: Views/ShareCard.swift:198,199,200 (그려 둔 카드 층을 이미지로. 배율은 기기 화소 밀도)
        val raw = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return null
        val k = 3 / ctx.resources.displayMetrics.density   // ios: Views/ShareCard.swift:199 r.scale = 3 (1pt = 3화소)
        val bmp = android.graphics.Bitmap.createScaledBitmap(raw, (raw.width * k).toInt(), (raw.height * k).toInt(), true)
        val d = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.forLanguageTag("ko_KR".replace('_', '-')))   // ios: Views/ShareCard.swift:201
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "${LocalDate.now().format(d)}-${list[picked].name}-naeilo.png")   // ios: Views/ShareCard.swift:202
        runCatching { file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }.getOrElse { return null }   // ios: Views/ShareCard.swift:203
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)   // ios: Views/ShareCard.swift:204
    }

    LaunchedEffect(Unit) { if (party) picked = 8 }   // ios: Views/ShareCard.swift:101
    LaunchedEffect(shareURL) { shareURL?.let { ActivitySheet.open(ctx, it); shareURL = null } }   // ios: Views/ShareCard.swift:102

    // ios: Views/ShareCard.swift:51,99,100
    Column(Modifier.fillMaxSize()) {
        SheetBar("미션 위젯 공유", trailing = "닫기", onClick = dismiss)
        // ios: Views/ShareCard.swift:52,53,96,98
        Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            T("보낼 위젯 고르기", style = appFont(13f), color = Theme.sub)   // ios: Views/ShareCard.swift:54
            // ios: Views/ShareCard.swift:55,56 (4열, 사이 8)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.indices.filter { party || it < 8 }.chunked(4).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { i ->
                            val on = i == picked   // ios: Views/ShareCard.swift:57
                            // ios: Views/ShareCard.swift:58,59,63,64,65,66,68,69
                            Column(
                                Modifier.weight(1f).plainClickable { picked = i }.semantics(mergeDescendants = true) { selected = on }
                                    .defaultMinSize(minHeight = 64.dp)
                                    .background(if (on) Theme.teal else Theme.card, RoundedCornerShape(16.dp))
                                    .border(1.dp, if (on) Theme.teal else Theme.border, RoundedCornerShape(16.dp)),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            ) {
                                Sym(list[i].icon, 20f, if (on) Color.White else Theme.ink, scaled = false)   // ios: Views/ShareCard.swift:60
                                T(list[i].name, style = appFont(11f, FontWeight.SemiBold), color = if (on) Color.White else Theme.ink, maxLines = 1)   // ios: Views/ShareCard.swift:61
                            }
                        }
                        repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            // ios: Views/ShareCard.swift:72,74
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("위젯 모양", Modifier.weight(1f), style = appFont(13f), color = Theme.sub)   // ios: Views/ShareCard.swift:73
                T("기본: 폰 설정 (${ShareCard.modes[if (scheme) 1 else 0]})", style = appFont(11f), color = Theme.muted)   // 75 (아이폰 → 폰: 예외)
            }
            // ios: Views/ShareCard.swift:77,78
            ChipRow(items = ShareCard.modes.indices.map { it to ShareCard.modes[it] }, selection = curMode, onSelect = { mode = it }, fill = true)

            // ios: Views/ShareCard.swift:80 (카드를 그리며 층에도 남겨 둔다: 저장할 때 그대로 이미지로)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) { Card0() }
            }

            // ios: Views/ShareCard.swift:82,83,84,85,86
            InputField(myMsg, { v -> myMsg = if (v.length > 40) v.take(40) else v }, "내 메시지 (비우면 기본 대사)",
                Modifier.background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp)).defaultMinSize(minHeight = 44.dp).padding(horizontal = 14.dp),
                style = appFont(14f))

            ChipRow(items = listOf("hide" to "금액 숨기기", "show" to "금액 보이기"), selection = showAmount, onSelect = { showAmount = it }, fill = true)   // ios: Views/ShareCard.swift:88
            // ios: Views/ShareCard.swift:89,90,91,92,94 (Label + systemImage)
            Row(Modifier.fillMaxWidth().plainClickable { scope.launch { shareURL = rendered() } }.defaultMinSize(minHeight = 50.dp).background(Theme.teal, RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Sym("square.and.arrow.up", 16f, Color.White); Spacer(Modifier.width(6.dp))
                T("이미지로 저장 · 공유", style = appFont(16f, FontWeight.Bold), color = Color.White)
            }
        }
    }
}
