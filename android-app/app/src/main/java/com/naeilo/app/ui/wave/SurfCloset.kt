package com.naeilo.app.ui.wave

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.MeChar
import com.naeilo.app.ui.MeMotion
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.SheetBar
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.me.MeArt
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.core.NJson
import kotlinx.serialization.encodeToString

// 파도 타기 꾸미기: 보드·모자 (문자 격자 도트, 이미지 파일 없음). 뽑기 없음, 누적 점수·특별 조건으로 열린다.
// 시안 좌표(12칸 인물)를 2배로 키워 내 캐릭터 버퍼(MeArt 24×34)에 맞춘다: 모자는 머리 위(시안 y1 = 0줄), 보드는 발밑(시안 y11 = 34줄).

// ios: Views/Wave/SurfCloset.swift:5,6
sealed class SurfOpen {
    data object free : SurfOpen()
    data class score(val n: Int) : SurfOpen()
    data object hardClear : SurfOpen()
    data class fever(val n: Int) : SurfOpen()
    data class splash(val n: Int) : SurfOpen()
    data class snap(val n: Int) : SurfOpen()
}

// ios: Views/Wave/SurfCloset.swift:9
data class SurfItem(
    val id: Int,   // ios: Views/Wave/SurfCloset.swift:10
    val name: String,   // ios: Views/Wave/SurfCloset.swift:11
    /** 모자: 왼쪽 위 (시안 좌표). 보드: 윗줄 x1 y11, 아랫줄 x2 y12 고정 */
    val x: Int, val y: Int,   // ios: Views/Wave/SurfCloset.swift:13
    val rows: List<String>,   // ios: Views/Wave/SurfCloset.swift:14
    val deco: List<Triple<Int, Int, String>> = emptyList(),   // 한 글자 문자열   // ios: Views/Wave/SurfCloset.swift:15
    val open: SurfOpen,   // ios: Views/Wave/SurfCloset.swift:16
)

// ios: Views/Wave/SurfCloset.swift:19
object SurfCloset {
    // ios: Views/Wave/SurfCloset.swift:20
    val palette: Map<Char, Long> = mapOf<String, Long>(
        "c" to 0xF06A4A, "C" to 0xC2410C, "y" to 0xFFD84D, "Y" to 0xE0A800, "o" to 0xF59E0B, "b" to 0x8A5A2B,   // ios: Views/Wave/SurfCloset.swift:21
        "g" to 0x3FAE5A, "G" to 0x1E7A3A, "r" to 0xE8505B, "k" to 0x14304D, "w" to 0xFFFFFF, "s" to 0x9FD3F5,   // ios: Views/Wave/SurfCloset.swift:22
        "p" to 0xF7A08A, "v" to 0x8B6FD6, "B" to 0x2F7FD8, "a" to 0x8A9BB0, "A" to 0x5C6B7D, "h" to 0xE8D3A8, "q" to 0xF2C14E,   // ios: Views/Wave/SurfCloset.swift:23
    ).mapKeys { it.key[0] }   // 한 글자 문자열 → 글자

    // ios: Views/Wave/SurfCloset.swift:26
    val boards: List<SurfItem> = listOf(
        SurfItem(id = 0, name = "코랄", x = 1, y = 11, rows = listOf("cccccccccc", ".CCCCCCCC"), open = SurfOpen.free),   // ios: Views/Wave/SurfCloset.swift:27
        SurfItem(id = 1, name = "바나나", x = 1, y = 11, rows = listOf("byyyyyyyyb", ".oooooooo"), open = SurfOpen.score(500)),   // ios: Views/Wave/SurfCloset.swift:28
        SurfItem(id = 2, name = "수박", x = 1, y = 11, rows = listOf("rrkrrrrkrr", ".GgGgGgGg"), open = SurfOpen.score(1200)),   // ios: Views/Wave/SurfCloset.swift:29
        SurfItem(id = 3, name = "구름", x = 1, y = 11, rows = listOf("wwwwwwwwww", ".ssssssss"),   // ios: Views/Wave/SurfCloset.swift:30
                 deco = listOf(Triple(1, 10, "w"), Triple(2, 10, "w"), Triple(10, 10, "w")), open = SurfOpen.score(2000)),   // ios: Views/Wave/SurfCloset.swift:31
        SurfItem(id = 4, name = "무지개", x = 1, y = 11, rows = listOf("rroyygBBvv", ".royygBBv"), open = SurfOpen.score(3200)),   // ios: Views/Wave/SurfCloset.swift:32
        SurfItem(id = 5, name = "상어", x = 1, y = 11, rows = listOf("aaaaaaaaka", ".wwwwwwww"),   // ios: Views/Wave/SurfCloset.swift:33
                 deco = listOf(Triple(2, 10, "a"), Triple(1, 9, "a")), open = SurfOpen.score(5000)),   // ios: Views/Wave/SurfCloset.swift:34
        SurfItem(id = 6, name = "캔들", x = 1, y = 11, rows = listOf("ggrgggrrgg", ".kkkkkkkk"),   // ios: Views/Wave/SurfCloset.swift:35
                 deco = listOf(Triple(2, 10, "g"), Triple(8, 10, "r")), open = SurfOpen.hardClear),   // ios: Views/Wave/SurfCloset.swift:36
        SurfItem(id = 7, name = "황금", x = 1, y = 11, rows = listOf("yyyyyyyyyy", ".YYYYYYYY"),   // ios: Views/Wave/SurfCloset.swift:37
                 deco = listOf(Triple(0, 9, "q"), Triple(11, 10, "q"), Triple(12, 8, "q")), open = SurfOpen.fever(10)),   // ios: Views/Wave/SurfCloset.swift:38
    )

    // ios: Views/Wave/SurfCloset.swift:41
    val hats: List<SurfItem> = listOf(
        SurfItem(id = 0, name = "없음", x = 0, y = 0, rows = emptyList(), open = SurfOpen.free),   // ios: Views/Wave/SurfCloset.swift:42
        SurfItem(id = 1, name = "밀짚", x = 1, y = -1, rows = listOf("..yyyyyy..", "..rrrrrr..", "oyyyyyyyyo"), open = SurfOpen.score(300)),   // ios: Views/Wave/SurfCloset.swift:43
        SurfItem(id = 2, name = "비니", x = 3, y = -3, rows = listOf("..ww..", ".BBBB.", "BBBBBB", "ssssss"), open = SurfOpen.score(800)),   // ios: Views/Wave/SurfCloset.swift:44
        SurfItem(id = 3, name = "꽃핀", x = 7, y = 0, rows = listOf(".p.", "pyp", ".p."), open = SurfOpen.score(1500)),   // ios: Views/Wave/SurfCloset.swift:45
        SurfItem(id = 4, name = "고깔", x = 4, y = -3, rows = listOf(".yy.", ".vv.", "vyyv", "vvvv"), open = SurfOpen.score(2500)),   // ios: Views/Wave/SurfCloset.swift:46
        SurfItem(id = 5, name = "개구리", x = 2, y = -2, rows = listOf(".gg..gg.", "gwkggwkg", "gggggggg", ".gggggg."), open = SurfOpen.score(4000)),   // ios: Views/Wave/SurfCloset.swift:47
        SurfItem(id = 6, name = "곰 귀", x = 2, y = -1, rows = listOf("bb....bb", "bp....pb", ".bbbbbb."), open = SurfOpen.splash(20)),   // ios: Views/Wave/SurfCloset.swift:48
        SurfItem(id = 7, name = "황소 뿔", x = 0, y = -2, rows = listOf("h..........h", "h..........h", ".hh......hh.", "..A......A.."), open = SurfOpen.snap(10)),   // ios: Views/Wave/SurfCloset.swift:49
    )

    // MARK: 저장 (앱 설정 정수 몇 개)
    private val d: SharedPreferences get() = store   // ios: Views/Wave/SurfCloset.swift:53
    val total: Int get() = d.getInt("surf.total", 0)   // ios: Views/Wave/SurfCloset.swift:54
    val fevers: Int get() = d.getInt("surf.fever", 0)   // ios: Views/Wave/SurfCloset.swift:55
    val splashes: Int get() = d.getInt("surf.splash", 0)   // ios: Views/Wave/SurfCloset.swift:56
    val snaps: Int get() = d.getInt("surf.snap", 0)   // ios: Views/Wave/SurfCloset.swift:57
    val hardCleared: Boolean get() = d.getInt("surf.hard", 0) > 0   // ios: Views/Wave/SurfCloset.swift:58
    // ios: Views/Wave/SurfCloset.swift:59
    var board: Int
        get() { val v = d.getInt("surf.board", 0); return if (isOpen(boards[min(max(v, 0), boards.size - 1)])) v else 0 }   // ios: Views/Wave/SurfCloset.swift:60
        set(newValue) { d.edit().putInt("surf.board", newValue).apply(); keep() }   // ios: Views/Wave/SurfCloset.swift:61
    // ios: Views/Wave/SurfCloset.swift:63
    var hat: Int
        get() { val v = d.getInt("surf.hat", 0); return if (isOpen(hats[min(max(v, 0), hats.size - 1)])) v else 0 }   // ios: Views/Wave/SurfCloset.swift:64
        set(newValue) { d.edit().putInt("surf.hat", newValue).apply(); keep() }   // ios: Views/Wave/SurfCloset.swift:65

    // MARK: 앱을 지웠다 깔아도 남게: iOS 는 키체인에 한 벌. 안드로이드는 따로 둔 설정 파일 한 벌(자동 백업에 실려 새 폰·다시 설치 때 돌아온다)
    /** 파도 타기 정수 키 전부: 꾸미기 + 섬 별·최고 점수 (Backup 파일에도 같이 넣는다) */
    val keys: List<String> = listOf("surf.total", "surf.fever", "surf.splash", "surf.snap", "surf.hard", "surf.board", "surf.hat") +   // ios: Views/Wave/SurfCloset.swift:70
        WaveMap.order.flatMap { listOf("wave.star.${it.raw}", "wave.best.${it.raw}") }   // ios: Views/Wave/SurfCloset.swift:71
    /** 키체인 자리: 서비스 이름 = 설정 파일 이름, 계정 = 키 */
    private val kc = Pair("com.naeilo.game", "wave.v1")   // ios: Views/Wave/SurfCloset.swift:72
    private val kcFile: SharedPreferences get() = Defaults.ctx.getSharedPreferences(kc.first, Context.MODE_PRIVATE)
    /** 처음 읽을 때 한 번: 이 폰 앱 설정이 비어 있으면 따로 둔 한 벌에서 되살린다 */
    // ios: Views/Wave/SurfCloset.swift:74
    val store: SharedPreferences by lazy {
        val u = Defaults.prefs   // ios: Views/Wave/SurfCloset.swift:75
        if (!keys.all { !u.contains(it) }) return@lazy u   // ios: Views/Wave/SurfCloset.swift:76
        val q = kcFile.getString(kc.second, null)   // ios: Views/Wave/SurfCloset.swift:77,78
        // ios: Views/Wave/SurfCloset.swift:79,80
        val m = q?.let { runCatching { NJson.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
        if (m != null) {
            val e = u.edit(); for ((k, v) in m) { if (k in keys) e.putInt(k, v) }; e.apply()   // ios: Views/Wave/SurfCloset.swift:81
        }
        u   // ios: Views/Wave/SurfCloset.swift:83
    }   // ios: Views/Wave/SurfCloset.swift:84
    // ios: Views/Wave/SurfCloset.swift:85
    fun keep() {
        val m = keys.associateWith { d.getInt(it, 0) }   // ios: Views/Wave/SurfCloset.swift:86
        val data = runCatching { NJson.encodeToString(m) }.getOrNull() ?: return   // ios: Views/Wave/SurfCloset.swift:87
        // ios: Views/Wave/SurfCloset.swift:88,89,90 (있으면 고치고 없으면 더한다: 한 줄 쓰기)
        kcFile.edit().putString(kc.second, data).apply()
    }

    // ios: Views/Wave/SurfCloset.swift:94
    fun isOpen(i: SurfItem): Boolean = when (val o = i.open) {   // ios: Views/Wave/SurfCloset.swift:95
        SurfOpen.free -> true   // ios: Views/Wave/SurfCloset.swift:96
        is SurfOpen.score -> total >= o.n   // ios: Views/Wave/SurfCloset.swift:97
        SurfOpen.hardClear -> hardCleared   // ios: Views/Wave/SurfCloset.swift:98
        is SurfOpen.fever -> fevers >= o.n   // ios: Views/Wave/SurfCloset.swift:99
        is SurfOpen.splash -> splashes >= o.n   // ios: Views/Wave/SurfCloset.swift:100
        is SurfOpen.snap -> snaps >= o.n   // ios: Views/Wave/SurfCloset.swift:101
    }

    /** 열리는 조건 (잠긴 칸에 짧게) */
    // ios: Views/Wave/SurfCloset.swift:106
    fun need(i: SurfItem): String = when (val o = i.open) {   // ios: Views/Wave/SurfCloset.swift:107
        SurfOpen.free -> ""   // ios: Views/Wave/SurfCloset.swift:108
        is SurfOpen.score -> "${o.n}점"   // ios: Views/Wave/SurfCloset.swift:109
        SurfOpen.hardClear -> "어려움 완주"   // ios: Views/Wave/SurfCloset.swift:110
        is SurfOpen.fever -> "피버 ${min(fevers, o.n)}/${o.n}"   // ios: Views/Wave/SurfCloset.swift:111
        is SurfOpen.splash -> "풍덩 ${min(splashes, o.n)}/${o.n}"   // ios: Views/Wave/SurfCloset.swift:112
        is SurfOpen.snap -> "딱! ${min(snaps, o.n)}/${o.n}"   // ios: Views/Wave/SurfCloset.swift:113
    }

    /** 한 판 결과를 쌓고 새로 열린 보드·모자를 돌려준다 (누적 점수는 줄지 않는다) */
    // ios: Views/Wave/SurfCloset.swift:118,119
    fun add(score: Int, fever: Int = 0, splash: Int = 0, snap: Int = 0, hardClear: Boolean = false): List<SurfItem> {
        val all = boards + hats   // ios: Views/Wave/SurfCloset.swift:120
        val before = all.map { isOpen(it) }   // ios: Views/Wave/SurfCloset.swift:121
        val e = d.edit()
        e.putInt("surf.total", total + max(score, 0))   // ios: Views/Wave/SurfCloset.swift:122
        e.putInt("surf.fever", fevers + max(fever, 0))   // ios: Views/Wave/SurfCloset.swift:123
        e.putInt("surf.splash", splashes + max(splash, 0))   // ios: Views/Wave/SurfCloset.swift:124
        e.putInt("surf.snap", snaps + max(snap, 0))   // ios: Views/Wave/SurfCloset.swift:125
        if (hardClear) e.putInt("surf.hard", 1)   // ios: Views/Wave/SurfCloset.swift:126
        e.apply()
        keep()   // ios: Views/Wave/SurfCloset.swift:127
        return all.zip(before).filter { (i, b) -> !b && isOpen(i) }.map { it.first }   // ios: Views/Wave/SurfCloset.swift:128
    }

    /** 다음 점수 선물: 이름·남은 점수·막대 비율 (모두 열렸으면 null) */
    class Gift(val item: SurfItem, val left: Int, val ratio: Double)
    // ios: Views/Wave/SurfCloset.swift:132
    fun nextGift(): Gift? {
        // ios: Views/Wave/SurfCloset.swift:133
        val steps = (boards + hats).mapNotNull { i ->
            val o = i.open; if (o is SurfOpen.score) Pair(i, o.n) else null   // ios: Views/Wave/SurfCloset.swift:134
        }.sortedBy { it.second }   // ios: Views/Wave/SurfCloset.swift:135
        val t = total
        val next = steps.firstOrNull { it.second > t } ?: return null   // ios: Views/Wave/SurfCloset.swift:136
        val prev = steps.lastOrNull { it.second <= t }?.second ?: 0   // ios: Views/Wave/SurfCloset.swift:137
        return Gift(next.first, next.second - t, (t - prev).toDouble() / max(next.second - prev, 1).toDouble())   // ios: Views/Wave/SurfCloset.swift:138
    }

    private fun min(a: Int, b: Int) = kotlin.math.min(a, b)
    private fun max(a: Int, b: Int) = kotlin.math.max(a, b)
}

/** 보드 위 내 캐릭터 + 모자. 도트 하나 = dot pt. hideHat: 피버 왕관이 뜰 때 모자 숨기기 */
// ios: Views/Wave/SurfCloset.swift:143,152
@Composable
fun SurfRider(
    me: MeChar = MeChar.current ?: MeChar(),   // ios: Views/Wave/SurfCloset.swift:144
    board: Int = SurfCloset.board,   // ios: Views/Wave/SurfCloset.swift:145
    hat: Int = SurfCloset.hat,   // ios: Views/Wave/SurfCloset.swift:146
    hideHat: Boolean = false,   // ios: Views/Wave/SurfCloset.swift:147
    dot: Float = 3f,   // ios: Views/Wave/SurfCloset.swift:148
    modifier: Modifier = Modifier,
) {
    val img = remember(me) { MeMotion.image(me)?.asImageBitmap() }   // ios: Views/Wave/SurfCloset.swift:153
    // ios: Views/Wave/SurfCloset.swift:154,171
    Box(modifier.size((dot * SurfRiderBox.cw).dp, (dot * SurfRiderBox.ch).dp), contentAlignment = Alignment.TopStart) {
        // ios: Views/Wave/SurfCloset.swift:155,156,157,158
        if (img != null) {
            Image(img, null, Modifier.offset((dot * SurfRiderBox.ox).dp, (dot * (SurfRiderBox.oy + MeArt.H) - dot * img.height).dp).size((dot * img.width).dp, (dot * img.height).dp),
                filterQuality = FilterQuality.None, contentScale = ContentScale.FillBounds)
        }
        // ios: Views/Wave/SurfCloset.swift:160
        Canvas(Modifier.fillMaxSize()) {
            val k = density   // 캔버스는 화소, 도트는 pt
            val b = SurfCloset.boards[kotlin.math.min(kotlin.math.max(board, 0), SurfCloset.boards.size - 1)]   // ios: Views/Wave/SurfCloset.swift:161
            // 보드: 시안 y → 버퍼 34 + 2(y-11)
            b.rows.forEachIndexed { r, row -> cells(this, dot * k, row, b.x, 11 + r, { 2 * it }) { MeArt.H + 2 * (it - 11) } }   // ios: Views/Wave/SurfCloset.swift:163
            for ((x, y, c) in b.deco) cells(this, dot * k, c, x, y, { 2 * it }) { MeArt.H + 2 * (it - 11) }   // ios: Views/Wave/SurfCloset.swift:164
            if (hideHat) return@Canvas   // ios: Views/Wave/SurfCloset.swift:165
            val h = SurfCloset.hats[kotlin.math.min(kotlin.math.max(hat, 0), SurfCloset.hats.size - 1)]   // ios: Views/Wave/SurfCloset.swift:166
            // 모자: 시안 y → 버퍼 2(y-1)
            h.rows.forEachIndexed { r, row -> cells(this, dot * k, row, h.x, h.y + r, { 2 * it }) { 2 * (it - 1) } }   // ios: Views/Wave/SurfCloset.swift:168
        }
    }
}

/** SurfRider 의 static 값: 캔버스는 버퍼 x -2...25, y -8...37 */
private object SurfRiderBox { const val ox = 2; const val oy = 8; const val cw = 28; const val ch = 46 }   // ios: Views/Wave/SurfCloset.swift:150

/** 한 줄 문자를 시안 1칸 = 버퍼 2×2 칸으로 칠한다 ('.' 투명) */
// ios: Views/Wave/SurfCloset.swift:175
private fun cells(ctx: DrawScope, dot: Float, row: String, x0: Int, y0: Int, x: (Int) -> Int, y: (Int) -> Int) {
    // ios: Views/Wave/SurfCloset.swift:176
    for ((i, ch) in row.withIndex()) {
        val rgb = SurfCloset.palette[ch] ?: continue   // ios: Views/Wave/SurfCloset.swift:177
        // ios: Views/Wave/SurfCloset.swift:178,179
        ctx.drawRect(hex(rgb), Offset(dot * (x(x0 + i) + SurfRiderBox.ox), dot * (y(y0) + SurfRiderBox.oy)), Size(dot * 2, dot * 2))
    }
}

/** 꾸미기 시트: 미리보기 + 보드·모자 칸(잠긴 칸은 흐리게, 조건 표시) + 다음 선물 막대 */
// ios: Views/Wave/SurfCloset.swift:185,194
@Composable
fun SurfClosetSheet(show: Boolean, dismiss: () -> Unit, onPick: () -> Unit = {}) {
    // ios: Views/Wave/SurfCloset.swift:198,209 (NavigationStack · 가운데 제목 · 오른쪽 닫기)
    Sheet(show, onDismiss = dismiss) {
        SurfClosetBody(dismiss, onPick)
    }
}

// ios: Views/Wave/SurfCloset.swift:197
@Composable
private fun SurfClosetBody(dismiss: () -> Unit, onPick: () -> Unit) {
    var board by remember { mutableIntStateOf(SurfCloset.board) }   // ios: Views/Wave/SurfCloset.swift:186
    var hat by remember { mutableIntStateOf(SurfCloset.hat) }   // ios: Views/Wave/SurfCloset.swift:187
    var hint by remember { mutableStateOf<String?>(null) }   // ios: Views/Wave/SurfCloset.swift:188
    // 열 때 한 번만 읽는다 (그릴 때마다 설정을 읽지 않게)
    val total = remember { SurfCloset.total }   // ios: Views/Wave/SurfCloset.swift:190
    val next = remember { SurfCloset.nextGift() }   // ios: Views/Wave/SurfCloset.swift:191
    // ios: Views/Wave/SurfCloset.swift:192,193
    val opened = remember { (SurfCloset.boards.filter { SurfCloset.isOpen(it) }.map { "보드${it.id}" } + SurfCloset.hats.filter { SurfCloset.isOpen(it) }.map { "모자${it.id}" }).toSet() }

    SheetBar("꾸미기", trailing = "닫기", onClick = dismiss)   // ios: Views/Wave/SurfCloset.swift:208,210
    // ios: Views/Wave/SurfCloset.swift:199,200,206
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SurfRider(board = board, hat = hat, dot = 5f, modifier = Modifier.padding(top = 8.dp))   // ios: Views/Wave/SurfCloset.swift:201
        Gift(total, next, hint)   // ios: Views/Wave/SurfCloset.swift:202
        ClosetSection("보드", SurfCloset.boards, board, opened, { hint = it }) { board = it; SurfCloset.board = it; onPick() }   // ios: Views/Wave/SurfCloset.swift:203
        ClosetSection("모자", SurfCloset.hats, hat, opened, { hint = it }) { hat = it; SurfCloset.hat = it; onPick() }   // ios: Views/Wave/SurfCloset.swift:204
    }
}

/** 맨 위: 지금 누적 점수 / 다음 선물까지 남은 점수 (크게). 잠긴 칸을 누르면 그 조건을 한 줄로 */
// ios: Views/Wave/SurfCloset.swift:215
@Composable
private fun Gift(total: Int, next: SurfCloset.Gift?, hint: String?) {
    val sub = Theme.sub
    // ios: Views/Wave/SurfCloset.swift:216
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ios: Views/Wave/SurfCloset.swift:217 (firstTextBaseline)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            T("누적 ${total}점", style = appFont(22f, FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum"))   // ios: Views/Wave/SurfCloset.swift:218 (.title2 = 22)
            Spacer(Modifier.weight(1f))   // ios: Views/Wave/SurfCloset.swift:219
            if (next != null) T("다음까지 ${next.left}점", style = appFont(17f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"))   // ios: Views/Wave/SurfCloset.swift:220 (.headline = 17)
        }
        // ios: Views/Wave/SurfCloset.swift:222
        if (next != null) {
            ProgressBar(value = next.ratio, height = 4.dp, fill = Theme.blue, track = Theme.track)   // ios: Views/Wave/SurfCloset.swift:223 (기본 ProgressView: 파란 막대)
            T("다음 선물: ${next.item.name} · 파도 타기 점수가 쌓이면 저절로 열려요", style = appFont(12f), color = sub)   // ios: Views/Wave/SurfCloset.swift:224 (.caption = 12)
        } else {   // ios: Views/Wave/SurfCloset.swift:225
            T("점수 선물 다 받음", style = appFont(12f), color = sub)   // ios: Views/Wave/SurfCloset.swift:226
        }
        if (hint != null) T(hint, style = appFont(13f, FontWeight.SemiBold), color = hex(0xD9480F))   // ios: Views/Wave/SurfCloset.swift:228 (.footnote = 13)
    }
}

/** 잠긴 칸을 누르면 보이는 한 줄 */
// ios: Views/Wave/SurfCloset.swift:232
private fun closetHint(i: SurfItem): String = when (val o = i.open) {   // ios: Views/Wave/SurfCloset.swift:233
    SurfOpen.free -> ""   // ios: Views/Wave/SurfCloset.swift:234
    is SurfOpen.score -> "${i.name}: 누적 ${o.n}점이면 열려요"   // ios: Views/Wave/SurfCloset.swift:235
    SurfOpen.hardClear -> "${i.name}: 어려움 코스를 끝까지 타면 열려요"   // ios: Views/Wave/SurfCloset.swift:236
    is SurfOpen.fever -> "${i.name}: 피버 ${o.n}번이면 열려요 (${SurfCloset.need(i)})"   // ios: Views/Wave/SurfCloset.swift:237
    is SurfOpen.splash -> "${i.name}: 풍덩 ${o.n}번이면 열려요 (${SurfCloset.need(i)})"   // ios: Views/Wave/SurfCloset.swift:238
    is SurfOpen.snap -> "${i.name}: 딱! ${o.n}번이면 열려요 (${SurfCloset.need(i)})"   // ios: Views/Wave/SurfCloset.swift:239
}

// ios: Views/Wave/SurfCloset.swift:243
@Composable
private fun ClosetSection(title: String, items: List<SurfItem>, sel: Int, opened: Set<String>, setHint: (String?) -> Unit, pick: (Int) -> Unit) {
    val sub = Theme.sub
    // ios: Views/Wave/SurfCloset.swift:244
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(title, style = appFont(17f, FontWeight.SemiBold))   // ios: Views/Wave/SurfCloset.swift:245
        // GridItem(.adaptive(minimum: 76), spacing: 8): 들어가는 만큼 칸, 남는 폭은 나눠 갖는다
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = kotlin.math.max(1, ((maxWidth + 8.dp) / (76.dp + 8.dp)).toInt())   // ios: Views/Wave/SurfCloset.swift:195
            // ios: Views/Wave/SurfCloset.swift:246
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (line in items.chunked(cols)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ios: Views/Wave/SurfCloset.swift:247
                        for (i in line) {
                            val open = opened.contains("$title${i.id}")   // ios: Views/Wave/SurfCloset.swift:248
                            // ios: Views/Wave/SurfCloset.swift:249,259
                            Box(Modifier.weight(1f).plainClickable { if (open) { pick(i.id); setHint(null) } else setHint(closetHint(i)) }) {
                                // ios: Views/Wave/SurfCloset.swift:250,254,255,256,257
                                Column(
                                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).alpha(if (open) 1f else 0.4f)
                                        .background(sub.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        .border(2.dp, if (sel == i.id) Theme.blue else Color.Transparent, RoundedCornerShape(10.dp)),
                                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    T(i.name, style = appFont(12f, FontWeight.SemiBold))   // ios: Views/Wave/SurfCloset.swift:251
                                    T(if (open) " " else SurfCloset.need(i), style = appFont(11f), color = sub)   // ios: Views/Wave/SurfCloset.swift:252 (.caption2 = 11)
                                }
                            }
                        }
                        repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

