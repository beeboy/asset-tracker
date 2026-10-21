package com.naeilo.app.ui.shelter

import com.naeilo.app.data.Support
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.naeilo.app.AppModel
import com.naeilo.app.core.WItem
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.data.Friend
import com.naeilo.app.data.Lines
import com.naeilo.app.data.Shelter
import com.naeilo.app.data.Story
import com.naeilo.app.data.StoryBlock
import com.naeilo.app.data.StoryBook
import com.naeilo.app.data.StoryLang
import com.naeilo.app.data.friendName
import com.naeilo.app.data.meGate1
import com.naeilo.app.data.meGate1Text
import com.naeilo.app.data.meGate2
import com.naeilo.app.data.meUnlocked
import com.naeilo.app.data.meVoice
import com.naeilo.app.data.roomBG
import com.naeilo.app.data.voice
import com.naeilo.app.ui.CapsuleShape
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.CharFigure
import com.naeilo.app.ui.Fg
import com.naeilo.app.ui.FitText
import com.naeilo.app.ui.FriendMotion
import com.naeilo.app.ui.HDivider
import com.naeilo.app.ui.LocalNavBack
import com.naeilo.app.ui.LocalPal
import com.naeilo.app.ui.LocalReduceMotion
import com.naeilo.app.ui.LocalTabActive
import com.naeilo.app.ui.NavBar
import com.naeilo.app.ui.NavPage
import com.naeilo.app.ui.PixelN
import com.naeilo.app.ui.ProgressBar
import com.naeilo.app.ui.Screen
import com.naeilo.app.ui.Segmented
import com.naeilo.app.ui.Sheet
import com.naeilo.app.ui.Spinner
import com.naeilo.app.ui.Sym
import com.naeilo.app.ui.T
import com.naeilo.app.ui.TA
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.EaseOut
import com.naeilo.app.ui.analysis.InvestBranch
import com.naeilo.app.ui.appFont
import com.naeilo.app.ui.charIdle
import com.naeilo.app.ui.drawableId
import com.naeilo.app.ui.hex
import com.naeilo.app.ui.hexD
import com.naeilo.app.ui.ls
import com.naeilo.app.ui.me.CharPixel
import com.naeilo.app.ui.me.RoomImage
import com.naeilo.app.ui.me.ShelterBGBox
import com.naeilo.app.ui.plainClickable
import com.naeilo.app.ui.support.SupportView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

// 쉼터: 친구, 서재(외전 프롤로그~5장, 구독하면 6장~코다와 앱 연재판 1권), 돌아온 물건. 시장 숫자와는 상관없고 내 행동으로만 열린다.
// 쉼터·서재·읽기 화면에서 여는 길(NavigationLink(value:))은 홈 길(homePath)에 붙는다. 이 화면들은 홈 탭에서만 열린다.

/** 홈 길에서 여는 쉼터 화면 (navigationTitle) */
// ios: Views/ShelterView.swift:25
fun shelterPage(m: AppModel, onlyLibrary: Boolean = false): NavPage = NavPage(if (onlyLibrary) "서재" else "쉼터") { ShelterView(m, onlyLibrary) }

/** 읽기 화면: 어둡게 읽을 때 이동 막대도 같은 바탕이라 막대를 화면이 그린다 */
fun readerPage(m: AppModel, index: Int, book: StoryBook = StoryBook.side): NavPage =
    NavPage(if (book == StoryBook.side) "서재" else "본편 1권", bar = false) { ReaderView(m, index, book) }

/** 인물 자세히: 갈래에 따라 제목이 바뀌어 막대를 화면이 그린다 */
fun characterPage(m: AppModel, friend: Friend, invest: Boolean = false, focus: Int? = null): NavPage =
    NavPage(friend.name, bar = false) { CharacterDetailView(m, friend, invest, focus) }

/** NavigationLink(value:): 홈 길에 한 칸 민다 */
private fun AppModel.push(route: String) { homePath = homePath + route }

/** 서재 묶음 (Swift (id:, book:, idx:) 튜플) */
private class StoryGroup(val id: String, val book: StoryBook, val idx: List<Int>)
/** 본편 1권의 부 (Swift (id:, name:, idx:) 튜플) */
private class StoryPart(val id: String, val name: String, val idx: MutableList<Int>)

/** ShelterView 의 @State 들과 계산 값 */
private class ShelterState(m: AppModel) {
    var sel by mutableStateOf("seri")   // ios: Views/ShelterView.swift:6
    var item by mutableStateOf<String?>(null)   // ios: Views/ShelterView.swift:7
    var support by mutableStateOf(false)   // ios: Views/ShelterView.swift:8
    val story = Story.shared   // ios: Views/ShelterView.swift:9
    val fold = mutableStateMapOf<String, Boolean>()   // ios: Views/ShelterView.swift:10 손으로 펼치고 접은 묶음 (없으면 읽는 묶음만 펼침)

    init { sel = m.shelterSel ?: m.homeFriend }   // ios: Views/ShelterView.swift:26,27 (처음 그릴 때 바로: 깜빡임 없이)

    // 서재 묶음 접기: 외전 프롤로그~5장 · 6장~코다 · 본편 1권 1부 · 2부 · 3부 … 중에 가장 최근에 읽고 있는 묶음 하나만 펼치고 나머지는 접는다.
    // 최근에 읽은 장을 끝까지 읽었으면 그다음 장이 든 묶음을 펼친다 (한 묶음을 다 읽으면 다음 묶음이 저절로 펼쳐진다).
    // 읽은 기록이 없으면 다 읽지 않은 첫 묶음. 손으로 펼치고 접은 것(fold)이 이보다 먼저다
    // ios: Views/ShelterView.swift:349
    fun groups(): List<StoryGroup> {
        val paid = if (story.count(StoryBook.side) > 6) (6 until story.count(StoryBook.side)).toList() else emptyList()   // ios: Views/ShelterView.swift:350
        val g = mutableListOf(StoryGroup("side0", StoryBook.side, (0 until 6).toList()), StoryGroup("side1", StoryBook.side, paid))   // ios: Views/ShelterView.swift:351
        if (vol1Shown()) g += vol1Parts().map { StoryGroup(it.id, StoryBook.vol1, it.idx) }   // ios: Views/ShelterView.swift:352
        return g   // ios: Views/ShelterView.swift:353
    }
    // ios: Views/ShelterView.swift:355
    fun current(m: AppModel): String? {
        val gs = groups()   // ios: Views/ShelterView.swift:356
        val k = story.last ?: m.readLast; if (k != null) {   // ios: Views/ShelterView.swift:357 예전 기록은 외전 이어 읽기 장
            val b = if (k >= 1000) StoryBook.vol1 else StoryBook.side; val i = if (k >= 1000) k - 1000 else k   // ios: Views/ShelterView.swift:358
            val n = if (story.isDone(b, i) && i + 1 < story.count(b)) i + 1 else i   // ios: Views/ShelterView.swift:359
            // ios: Views/ShelterView.swift:360
            val g = gs.firstOrNull { it.book == b && it.idx.contains(n) } ?: gs.firstOrNull { it.book == b && it.idx.contains(i) }; if (g != null) return g.id
        }
        return gs.firstOrNull { g -> g.idx.isEmpty() || !g.idx.all { story.isDone(g.book, it) } }?.id   // ios: Views/ShelterView.swift:362
    }
    fun isOpen(id: String, cur: String?): Boolean = fold[id] ?: (id == cur)   // ios: Views/ShelterView.swift:364

    /** 본편 1권의 부: 장 제목 앞의 'N부'('Part N, …') 로 묶고, 이름은 그 부 첫 장의 '1부. 거부할 권리'('Part One — The Right to Refuse') 같은 절 제목 */
    // ios: Views/ShelterView.swift:383
    fun vol1Parts(): List<StoryPart> {
        val out = mutableListOf<StoryPart>()   // ios: Views/ShelterView.swift:384
        for (i in 0 until story.count(StoryBook.vol1)) {   // ios: Views/ShelterView.swift:385
            val ch = story.chapter(i, StoryBook.vol1); val title = ch?.title ?: ""   // ios: Views/ShelterView.swift:386
            val p = if (title.startsWith("Part")) title.split(",")[0] else title.split(" ").firstOrNull { it.isNotEmpty() } ?: ""   // ios: Views/ShelterView.swift:387
            if (out.lastOrNull()?.id != "vol1-" + p) {   // ios: Views/ShelterView.swift:388
                val h = ch?.blocks?.firstOrNull { it.k == "h" && (it.t.startsWith(p) || it.t.startsWith("Part ")) }?.t   // ios: Views/ShelterView.swift:389
                out.add(StoryPart(id = "vol1-" + p, name = h?.replace(". ", " · ")?.replace(" — ", " · ") ?: p, idx = mutableListOf()))   // ios: Views/ShelterView.swift:390
            }
            out[out.size - 1].idx.add(i)   // ios: Views/ShelterView.swift:392
        }
        return out   // ios: Views/ShelterView.swift:394
    }

    /** 앱 연재판 1권: 연 구독 또는 월 구독 9개월 이상이면 서버에서 받는다. 받은 장은 구독을 멈춰도 그대로 */
    fun vol1Shown(): Boolean = Support.shared.vol1Open || story.count(StoryBook.vol1) > 0   // ios: Views/ShelterView.swift:422
}

// ios: Views/ShelterView.swift:4,5,12,14
@Composable
fun ShelterView(m: AppModel, onlyLibrary: Boolean = false) {
    val s = remember { ShelterState(m) }
    // ios: Views/ShelterView.swift:28 다음 장을 연 장은 다 읽은 것으로 (예전 기록)
    LaunchedEffect(Unit) { for (i in 0 until 5) if (m.readPos.contains(i + 1)) s.story.markDone(StoryBook.side, i) }
    // ios: Views/ShelterView.swift:15,24
    Column(Modifier.fillMaxSize().background(Theme.bg).verticalScroll(rememberScrollState())) {
        Screen(Modifier.padding(top = 8.dp)) {   // ios: Views/ShelterView.swift:22
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/ShelterView.swift:16
                if (onlyLibrary) {   // ios: Views/ShelterView.swift:17
                    Library(m, s)   // ios: Views/ShelterView.swift:18
                    if (s.vol1Shown()) Vol1(m, s)   // ios: Views/ShelterView.swift:19
                } else ShelterBody(m, s)   // ios: Views/ShelterView.swift:20
            }
        }
    }
    Sheet(s.support, { s.support = false }) { SupportView(m) }   // ios: Views/ShelterView.swift:30
}

// ios: Views/ShelterView.swift:33
@Composable
private fun ShelterBody(m: AppModel, s: ShelterState) {
    T("꾸준히 쓰면 물건·이야기가 돌아와요", style = appFont(13f), color = Theme.sub)   // ios: Views/ShelterView.swift:34
    Hero(m, s)   // ios: Views/ShelterView.swift:35
    val met = (0 until 5).count { m.friendOn(it) }   // ios: Views/ShelterView.swift:36
    Header("만난 친구", "$met/5")   // ios: Views/ShelterView.swift:37
    // ios: Views/ShelterView.swift:38,39
    Grid5(Shelter.friends.size, cols = 5, hGap = 5.dp, vGap = 6.dp) { i -> FriendTile(m, s, i, Shelter.friends[i]) }
    T("눌러서 홈 인물 바꾸기", style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:41
    // 내 캐릭터는 친구와 따로 한 줄
    MeTile(m, s)   // ios: Views/ShelterView.swift:43
    Header("이어서 읽기", "")   // ios: Views/ShelterView.swift:44
    ContinueCard(m, s)   // ios: Views/ShelterView.swift:45
    // ios: Views/ShelterView.swift:46,48,49,50,51
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp))
        .plainClickable { m.push("library") }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        // ios: Views/ShelterView.swift:47
        T("서재 전체", style = appFont(14f, FontWeight.Bold), color = Theme.ink); Spacer(Modifier.weight(1f)); T("›", color = Theme.muted)
    }
    val placed = (0 until m.itemsOn).count { !m.itemsOff.contains(Shelter.items[it].id) }   // ios: Views/ShelterView.swift:52
    Header("돌아온 물건", "$placed/${Shelter.items.size} 배치")   // ios: Views/ShelterView.swift:53
    Room(m, s)   // ios: Views/ShelterView.swift:54
    // ios: Views/ShelterView.swift:55,56
    Grid5(Shelter.items.size, cols = 5, hGap = 6.dp, vGap = 6.dp) { i -> ItemTile(m, s, i, Shelter.items[i]) }
    ItemNote(m, s)   // ios: Views/ShelterView.swift:58
    OpenTable()   // ios: Views/ShelterView.swift:59
    ShelterBGBox(m, s.support) { s.support = it }   // ios: Views/ShelterView.swift:60
}

/** LazyVGrid(columns: 같은 폭 cols 칸): 칸이 적어 한 번에 그린다 */
@Composable
private fun Grid5(n: Int, cols: Int, hGap: Dp, vGap: Dp, cell: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
        for (r in 0 until (n + cols - 1) / cols) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(hGap), verticalAlignment = Alignment.CenterVertically) {
            for (c in 0 until cols) { val i = r * cols + c; Box(Modifier.weight(1f)) { if (i < n) cell(i) } }
        }
    }
}

/** Spacer(minLength:): 남은 폭을 채운다 */
@Composable
private fun RowScope.FlexSpacer(min: Dp) = Spacer(Modifier.weight(1f).widthIn(min = min))

/** overlay(RoundedRectangle.stroke(lineWidth:, dash:)): 모서리 안쪽에 선 (dash 가 null 이면 실선) */
private fun Modifier.dashBorder(color: Color, radius: Dp, width: Dp, dash: FloatArray?): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRoundRect(color, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), cornerRadius = CornerRadius(radius.toPx() - w / 2),
        style = Stroke(w, pathEffect = dash?.let { PathEffect.dashPathEffect(floatArrayOf(it[0].dp.toPx(), it[1].dp.toPx())) }))
}

/** .grayscale(g).brightness(b) 색 거르개 (둘 다 0 이면 null) */
private fun toneFilter(gray: Float, bright: Float): ColorFilter? {
    if (gray == 0f && bright == 0f) return null
    val v = ColorMatrix().apply { setToSaturation(1f - gray) }.values.copyOf()
    val off = bright * 255f
    v[4] += off; v[9] += off; v[14] += off
    return ColorFilter.colorMatrix(ColorMatrix(v))
}

/** 안의 그림 전체에 .grayscale·.brightness 를 건다 */
private fun Modifier.tone(gray: Float, bright: Float): Modifier {
    val f = toneFilter(gray, bright) ?: return this
    val paint = Paint().apply { colorFilter = f }
    return drawWithContent { drawIntoCanvas { c -> c.saveLayer(Rect(Offset.Zero, size), paint); drawContent(); c.restore() } }
}

/** Image("이름").interpolation(.none): 없으면 null */
@Composable
private fun artPainter(name: String): Painter? {
    val id = drawableId(LocalContext.current, name)
    if (id == 0) return null
    val bmp = ImageBitmap.imageResource(id)
    return remember(bmp) { BitmapPainter(bmp, filterQuality = FilterQuality.None) }
}

/** 서버에서 받은 장의 표지: 푸는 일은 그리기 밖(Default)에서 한 번만 */
@Composable
private fun rememberCover(story: Story, i: Int, b: StoryBook): Painter? {
    val url = story.chapter(i, b)?.cover
    val bmp by produceState(url?.let { cachedCover(it) }, url) {
        if (url != null && value == null) value = withContext(Dispatchers.Default) { coverImage(url) }
    }
    return remember(bmp) { bmp?.let { BitmapPainter(it, filterQuality = FilterQuality.None) } }
}

/** 이어서 읽기: 마지막에 읽던 장 큰 카드 하나 + 작품별 진도 */
// ios: Views/ShelterView.swift:64
@Composable
private fun ContinueCard(m: AppModel, s: ShelterState) {
    val story = s.story
    // ios: Views/ShelterView.swift:65
    val i = m.readLast?.let { if (it < 6 && m.chapterOn(it)) it else null } ?: (0 until 6).firstOrNull { m.chapterOn(it) && !story.isDone(StoryBook.side, it) } ?: 0
    val ch = Shelter.chapters[i]; val on = m.chapterOn(i)   // ios: Views/ShelterView.swift:66
    val sideDone = (0 until 6).count { story.isDone(StoryBook.side, it) }   // ios: Views/ShelterView.swift:67
    val v1 = story.count(StoryBook.vol1)   // ios: Views/ShelterView.swift:68
    // ios: Views/ShelterView.swift:69,91,92,93
    Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val nm = if (on) ch.name else ""
        // ios: Views/ShelterView.swift:70,71,81,83
        Fg(Theme.ink) { Row(Modifier.fillMaxWidth().then(if (on) Modifier.plainClickable { m.push("read:$i") } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = "이어 읽기, ${ch.title} $nm" },   // Views/ShelterView.swift:84 (검사 예외: 보간 속 따옴표)
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ChapterCover(index = i, on = on, modifier = Modifier.size(72.dp, 46.dp))   // ios: Views/ShelterView.swift:72
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/ShelterView.swift:73
                T("『이종 공명』 · ${ch.title}", style = appFont(12f), color = Theme.sub)   // ios: Views/ShelterView.swift:74
                T(if (on) ch.name else "???", style = appFont(15f, FontWeight.Bold))   // ios: Views/ShelterView.swift:75
                ProgressBar(value = sideDone / 6.0, height = 4.dp, fill = Theme.teal, track = Theme.track)   // ios: Views/ShelterView.swift:76 (ProgressView 막대)
            }
            // ios: Views/ShelterView.swift:78,79
            Box(Modifier.size(40.dp, 40.dp).background(Theme.teal, CircleShape), contentAlignment = Alignment.Center) { Sym("play.fill", 14f, Color.White) }
        } }
        HDivider(Theme.line)   // ios: Views/ShelterView.swift:85
        ProgressLine("『이종 공명』", "$sideDone/6장")   // ios: Views/ShelterView.swift:86
        if (s.vol1Shown() && v1 > 0) {   // ios: Views/ShelterView.swift:87
            ProgressLine("본편 1권", "${(0 until v1).count { story.isDone(StoryBook.vol1, it) }}/${v1}장")   // ios: Views/ShelterView.swift:88
        }
    }
}
// ios: Views/ShelterView.swift:95,96
@Composable
private fun ProgressLine(t: String, r: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    T(t, style = appFont(13f)); Spacer(Modifier.weight(1f)); T(r, style = appFont(13f), color = Theme.sub)
}

/** 열리는 방법 (예전 맨 아래 긴 문단을 표로) */
// ios: Views/ShelterView.swift:100,101,114,115,116
@Composable
private fun OpenTable() = Column(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(16.dp)).border(1.dp, Theme.border, RoundedCornerShape(16.dp)).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)) {
    T("열리는 방법", style = appFont(15f, FontWeight.Bold))   // ios: Views/ShelterView.swift:102
    // ios: Views/ShelterView.swift:103
    listOf("물건·배경" to "하루 2개", "친구" to "이틀째부터 하루 1명", "외전 ~5장" to "앞 장 읽으면", "외전 6장~" to "구독").forEach { r ->
        Row {   // ios: Views/ShelterView.swift:104
            T(r.first, Modifier.width(130.dp).alignByBaseline(), style = appFont(13f, FontWeight.Bold))   // ios: Views/ShelterView.swift:105
            T(r.second, Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/ShelterView.swift:106
        }
    }
    // ios: Views/ShelterView.swift:109,110,111
    T("수익률과 무관 · 잃어도 계속 열려요", Modifier.fillMaxWidth().background(Theme.mintBg, RoundedCornerShape(10.dp)).padding(12.dp), style = appFont(13f, FontWeight.SemiBold))
    T("방 물건 누르면 빼기 · 위 칸에서 다시 놓기", style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:112
}

// ios: Views/ShelterView.swift:119,120
@Composable
private fun Header(t: String, r: String) = Row(Modifier.fillMaxWidth()) {
    T(t, Modifier.alignByBaseline(), style = appFont(15f, FontWeight.Bold)); Spacer(Modifier.weight(1f))   // ios: Views/ShelterView.swift:121
    T(r, Modifier.alignByBaseline(), style = appFont(13f), color = Theme.sub)   // ios: Views/ShelterView.swift:122
}

// ios: Views/ShelterView.swift:126
@Composable
private fun Hero(m: AppModel, s: ShelterState) {
    if (s.sel == "me" && m.me != null) MeHero(m) else FriendHero(m, s)   // ios: Views/ShelterView.swift:127
}

// ios: Views/ShelterView.swift:130
@Composable
private fun FriendHero(m: AppModel, s: ShelterState) {
    // ios: Views/ShelterView.swift:131
    val i = Shelter.friends.indexOfFirst { it.id == s.sel }.takeIf { it >= 0 } ?: 0; val f = Shelter.friends[i]; val real = m.friendOn(i)
    val on = real   // ios: Views/ShelterView.swift:132
    // ios: Views/ShelterView.swift:133,174,175,176
    Fg(hex(0xEEF0F7)) { Column(Modifier.fillMaxWidth().background(Theme.shelter, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/ShelterView.swift:134
            // ios: Views/ShelterView.swift:135,141,142
            Box(Modifier.background(Shelter.spriteBacking(f.id), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Box(Modifier.alpha(if (real) 1f else if (on) 0.75f else 1f)) {   // ios: Views/ShelterView.swift:140
                    // 만난 친구는 숨 쉬고, 누르면 한 바퀴 돌며 걷는다 (포즈 그림이 없으면 예전 그림)
                    // ios: Views/ShelterView.swift:137
                    if (on && FriendMotion.has(f.id)) Box(Modifier.size(84.dp, 119.dp), contentAlignment = Alignment.BottomCenter) { CharFigure(friend = f.id, dot = 3.4.dp) }
                    else PixelN((if (on) "spr_" else "sil_") + f.id, 84.dp, 119.dp)   // ios: Views/ShelterView.swift:138
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/ShelterView.swift:143
                T(if (on) f.name else "???", style = appFont(20f, FontWeight.Bold))   // ios: Views/ShelterView.swift:144
                // ios: Views/ShelterView.swift:145,146
                T(if (real) f.appearsText else if (on) "미리 보기 · 인터미션 ${i}주차에 만나요" else "인터미션 ${i}주차에 만나요", style = appFont(12f), color = hex(0x8FD0FF))
                // ios: Views/ShelterView.swift:147,148,149
                T(if (on) f.bio else "${i}주차 체크인: ${Shelter.weekSteps[max(0, i - 1)].task}", style = appFont(13f).ls(3f), color = hex(0xD5D9E6))
                if (on) T(f.line, style = appFont(13f, FontWeight.Bold), color = Theme.gold)   // ios: Views/ShelterView.swift:150
            }
            FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:152
        }
        if (on) {   // ios: Views/ShelterView.swift:154
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/ShelterView.swift:155
                // ios: Views/ShelterView.swift:156,158,159,161
                Box(Modifier.plainClickable { m.push("char:" + f.id) }.border(1.5.dp, hex(0x8FD0FF), CapsuleShape).defaultMinSize(minHeight = 40.dp).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center) {
                    T("자세히 ›", style = appFont(13f, FontWeight.Bold), color = hex(0xEEF0F7))   // ios: Views/ShelterView.swift:157
                }
                if (real && m.homeFriend != f.id) {   // ios: Views/ShelterView.swift:162
                    // ios: Views/ShelterView.swift:163,164,165
                    Box(Modifier.defaultMinSize(minHeight = 40.dp).background(Theme.mint, CapsuleShape).plainClickable { m.homeFriend = f.id }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        T("홈에 두기", style = appFont(13f, FontWeight.Bold), color = Theme.inkFixed)
                    }
                } else if (real) {   // ios: Views/ShelterView.swift:166
                    // ios: Views/ShelterView.swift:167,168
                    T("홈에 있음", Modifier.background(Theme.gold, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp), style = appFont(12f, FontWeight.Bold), color = Theme.inkFixed)
                }
                FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:170
            }
        }
    } }
}

// ios: Views/ShelterView.swift:179
@Composable
private fun FriendTile(m: AppModel, s: ShelterState, i: Int, f: Friend) {
    val on = m.friendOn(i); val cur = s.sel == f.id   // ios: Views/ShelterView.swift:180
    // ios: Views/ShelterView.swift:181,188,192 (누르면 sel 을 바꾼다)
    Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp).plainClickable { s.sel = f.id }.then(Modifier
        // ios: Views/ShelterView.swift:189
        .background(if (cur) Theme.mintBg else if (on) (if (f.id == "ir" || f.id == "sua") hexD(0xFFFFFF, 0x2C3846) else Theme.card) else hexD(0xF6F7F8, 0x1D252E), RoundedCornerShape(12.dp))
        .border(2.dp, if (cur) Theme.teal else if (on) Theme.border else Theme.track, RoundedCornerShape(12.dp))   // ios: Views/ShelterView.swift:190
        .semantics(mergeDescendants = true) { contentDescription = if (on) f.name else "잠긴 친구" }),   // ios: Views/ShelterView.swift:182,193
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelN((if (on) "spr_" else "sil_") + f.id, 34.dp, 48.dp)   // ios: Views/ShelterView.swift:183
        FitText(if (on) f.name else "???", appFont(12f, FontWeight.Bold), 0.8f, maxLines = 1)   // ios: Views/ShelterView.swift:184
        // ios: Views/ShelterView.swift:185,186
        T(if (on && m.homeFriend == f.id) "홈에 있음" else m.friendWhen(i), style = appFont(10f), color = Theme.sub)
    }
}

// 본인 칸: 비어 있으면 누르는 즉시 만들기 화면으로
// ios: Views/ShelterView.swift:197
@Composable
private fun MeTile(m: AppModel, s: ShelterState) {
    if (m.me == null) {   // ios: Views/ShelterView.swift:198
        MeTileLabel(m, s, Modifier.plainClickable { m.push("me") })   // ios: Views/ShelterView.swift:199
    } else {   // ios: Views/ShelterView.swift:200
        MeTileLabel(m, s, Modifier.plainClickable { s.sel = "me" })   // ios: Views/ShelterView.swift:201 Button
    }
}
// ios: Views/ShelterView.swift:204
@Composable
private fun MeTileLabel(m: AppModel, s: ShelterState, click: Modifier) {
    val cur = s.sel == "me"; val made = m.me != null; val open = m.meUnlocked   // ios: Views/ShelterView.swift:205
    val gates = listOf(m.meGate1 to m.meGate1Text, m.meGate2 to "평정 지수 성향 매칭")   // ios: Views/ShelterView.swift:206
    val done = gates.count { it.first }   // ios: Views/ShelterView.swift:207
    val st = if (open) "열림" else "2개 중 ${done}개 채움"; val nm = m.me?.shownName.orEmpty()
    // ios: Views/ShelterView.swift:208,223,224,225,226,227
    Fg(Theme.ink) { Column(click.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
        .background(if (cur) Theme.mintBg else if (made) Theme.card else Color.Transparent, RoundedCornerShape(12.dp))
        .dashBorder(if (cur) Theme.teal else if (made) Theme.border else Theme.dash, 12.dp, 2.dp, if (made) null else floatArrayOf(4f, 3f))
        .semantics(mergeDescendants = true) { contentDescription = if (made) "나, $nm, $st" else "나, 내 캐릭터 만들기" }   // Views/ShelterView.swift:228 (검사 예외: 보간 속 따옴표)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/ShelterView.swift:209
            // 잠겼을 땐 그림자
            // ios: Views/ShelterView.swift:211
            CharPixel(id = "me", width = 28.dp, height = 40.dp, modifier = Modifier.tone(0f, if (open) 0f else -1f).alpha(if (open) 1f else 0.45f), model = m)
            T("나" + (m.me?.let { " · " + it.shownName } ?: ""), Modifier.weight(1f, fill = false), style = appFont(14f, FontWeight.Bold), maxLines = 1)   // ios: Views/ShelterView.swift:212
            FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:213
            // ios: Views/ShelterView.swift:214,215
            T(if (!made) "만들기 ›" else if (m.homeFriendShown == "me") "홈에 있음" else if (open) "열림" else "2개 중 ${done}개", style = appFont(12f), color = if (made) Theme.sub else Theme.teal)
        }
        if (!open) {   // ios: Views/ShelterView.swift:217
            gates.withIndex().filter { !it.value.first }.forEach { g ->   // ios: Views/ShelterView.swift:218
                T("· " + g.value.second, style = appFont(12f), color = Theme.sub)   // ios: Views/ShelterView.swift:219
            }
        }
    } }
}

/** 내 캐릭터 소개: 투자 유형, 열림 조건 세 가지, 고치기·홈에 두기 */
// ios: Views/ShelterView.swift:232
@Composable
private fun MeHero(m: AppModel) {
    val open = m.meUnlocked; val k = Calm.kind(m.meVoice)   // ios: Views/ShelterView.swift:233
    // ios: Views/ShelterView.swift:234,274,275,276
    Fg(hex(0xEEF0F7)) { Column(Modifier.fillMaxWidth().background(Theme.shelter, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/ShelterView.swift:235
            // ios: Views/ShelterView.swift:236,239,240
            Box(Modifier.background(hex(0x232B42), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                // ios: Views/ShelterView.swift:237
                val me = m.me; if (me != null) Box(Modifier.size(84.dp, 119.dp), contentAlignment = Alignment.Center) { CharFigure(me = me, dot = 3.5.dp) } else CharPixel(id = "me", width = 84.dp, height = 119.dp, model = m)
            }
            Column(Modifier.weight(1f).widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/ShelterView.swift:241,248 (남은 폭)
                T(m.friendName("me"), style = appFont(20f, FontWeight.Bold))   // ios: Views/ShelterView.swift:242
                val kt = k?.title.orEmpty()
                // Views/ShelterView.swift:243 (검사 예외: 보간 속 따옴표)
                T("내 캐릭터 · $kt (${m.friendName(m.meVoice)} 말투)" + (if (m.calm.type != null) " · 평정 지수 매칭" else ""),
                  style = appFont(12f), color = hex(0x8FD0FF))   // ios: Views/ShelterView.swift:244
                // ios: Views/ShelterView.swift:245,246
                T(if (open) "홈과 위젯에서 기본 인물 대신 둘 수 있어요." else "세 가지를 채우면 홈과 위젯에 나와요. 그 전에는 쉼터에서만 보여요.", style = appFont(13f).ls(3f), color = hex(0xD5D9E6))
            }
        }
        if (!open) {   // ios: Views/ShelterView.swift:250
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/ShelterView.swift:251
                Gate(m.meGate1, "1", m.meGate1Text)   // ios: Views/ShelterView.swift:252
                // ios: Views/ShelterView.swift:253
                Gate(m.meGate2, "2", if (m.meGate2) "평정 지수 성향 매칭" else "평정 지수 성향 매칭 (기록 ${m.calm.stats().n}/${Calm.minEvents})")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/ShelterView.swift:256
            // ios: Views/ShelterView.swift:257,259,260,262
            Box(Modifier.plainClickable { m.push("me") }.border(1.5.dp, hex(0x8FD0FF), CapsuleShape).defaultMinSize(minHeight = 40.dp).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center) {
                T("고치기 ›", style = appFont(13f, FontWeight.Bold), color = hex(0xEEF0F7))   // ios: Views/ShelterView.swift:258
            }
            if (open && m.homeFriend != "me") {   // ios: Views/ShelterView.swift:263
                // ios: Views/ShelterView.swift:264,265,266
                Box(Modifier.defaultMinSize(minHeight = 40.dp).background(Theme.mint, CapsuleShape).plainClickable { m.homeFriend = "me" }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    T("홈에 두기", style = appFont(13f, FontWeight.Bold), color = Theme.inkFixed)
                }
            } else if (open) {   // ios: Views/ShelterView.swift:267
                // ios: Views/ShelterView.swift:268,269
                T("홈에 있음", Modifier.background(Theme.gold, CapsuleShape).padding(horizontal = 10.dp, vertical = 4.dp), style = appFont(12f, FontWeight.Bold), color = Theme.inkFixed)
            }
            FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:271
        }
    } }
}
// ios: Views/ShelterView.swift:278
@Composable
private fun Gate(ok: Boolean, @Suppress("UNUSED_PARAMETER") n: String, t: String) {
    val st = if (ok) "채움" else "아직"
    // Views/ShelterView.swift:286 (검사 예외: 보간 속 따옴표)
    // ios: Views/ShelterView.swift:279,285 (firstTextBaseline: 기호는 첫 줄 높이에 맞춘다)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$t, $st" }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        // ios: Views/ShelterView.swift:280,281
        Sym(if (ok) "checkmark.circle.fill" else "circle", 13f, if (ok) Theme.mint else hex(0x8A93AD), Modifier.padding(top = 1.dp))
        // ios: Views/ShelterView.swift:282,283
        T(t, style = appFont(13f, if (ok) FontWeight.Normal else FontWeight.SemiBold), color = if (ok) hex(0xA6ADC6) else hex(0xEEF0F7))
    }
}

// ios: Views/ShelterView.swift:289
@Composable
private fun Library(m: AppModel, s: ShelterState) {
    val story = s.story
    val scope = rememberCoroutineScope()
    val paid = if (story.count(StoryBook.side) > 6) (6 until story.count(StoryBook.side)).toList() else emptyList()   // ios: Views/ShelterView.swift:290
    val cur = s.current(m)   // ios: Views/ShelterView.swift:291
    LaunchedEffect(story.lang) { story.fetch(StoryBook.side) }   // ios: Views/ShelterView.swift:343 구독 중이면 6장부터 받아 둔다
    // ios: Views/ShelterView.swift:292,340,341,342
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Theme.card).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // ios: Views/ShelterView.swift:293
        val read0 = (0 until 6).count { story.isDone(StoryBook.side, it) }; FoldRow(s, "side0", "프롤로그 ~ 5장", "$read0/6 읽음", cur)
        if (s.isOpen("side0", cur)) { for (i in 0 until 6) {   // ios: Views/ShelterView.swift:294
            val on = m.chapterOn(i); val ch = Shelter.chapters[i]   // ios: Views/ShelterView.swift:295
            @Suppress("UNUSED_VARIABLE") val fi = Shelter.friends.indexOfFirst { it.id == ch.friend }.takeIf { it >= 0 } ?: 0   // ios: Views/ShelterView.swift:296
            HDivider(Theme.line)   // ios: Views/ShelterView.swift:311
            // ios: Views/ShelterView.swift:312,313
            val click = if (on) Modifier.plainClickable { m.push("read:$i") } else Modifier.plainClickable { s.sel = ch.friend }
            // ios: Views/ShelterView.swift:297,308,309,310
            Fg(if (on) Theme.ink else Theme.muted) { Row(click.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ChapterCover(index = i, on = on, modifier = Modifier.size(64.dp, 40.dp))   // ios: Views/ShelterView.swift:298
                T(ch.title, Modifier.width(44.dp), style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/ShelterView.swift:299
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/ShelterView.swift:300
                    T(if (on) ch.name else "???", style = appFont(14f, FontWeight.Bold))   // ios: Views/ShelterView.swift:301
                    // ios: Views/ShelterView.swift:302,303
                    T(if (on) (if (i == 0) "처음부터 열림" else "앞 장을 읽어 열림") else "앞 장을 끝까지 읽으면 열려요", style = appFont(12f), color = Theme.sub)
                }
                FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:305
                // ios: Views/ShelterView.swift:306
                T(if (on) (if (m.readPos.contains(i)) "이어 읽기" else "읽기") else "잠김", style = appFont(12f), color = Theme.muted)
            } }
        } }
        HDivider(Theme.line)   // ios: Views/ShelterView.swift:315
        // ios: Views/ShelterView.swift:316
        FoldRow(s, "side1", "6장 ~ 코다", if (paid.isEmpty()) "구독" else "${paid.count { story.isDone(StoryBook.side, it) }}/${paid.size} 읽음", cur)
        // 6장부터: 구독하면 서버에서 받은 장 (구독을 멈춰도 받은 장은 그대로), 아니면 누르면 구독 화면
        if (!s.isOpen("side1", cur)) {   // ios: Views/ShelterView.swift:318
        } else if (story.count(StoryBook.side) > 6) {   // ios: Views/ShelterView.swift:319
            for (i in 6 until story.count(StoryBook.side)) {   // ios: Views/ShelterView.swift:320
                HDivider(Theme.line)   // ios: Views/ShelterView.swift:321
                // ios: Views/ShelterView.swift:322,323
                PaidRow(rememberCover(story, i, StoryBook.side) ?: (if (i == 6) artPainter("art_ch6") else null), story.chapter(i)?.title ?: "", story.chapter(i)?.name ?: "", if ((story.pos[i] ?: 0) > 0) "이어 읽기" else "읽기", Modifier.plainClickable { m.push("read:$i") })
            }
        } else if (Support.shared.sideOpen) {   // ios: Views/ShelterView.swift:325
            HDivider(Theme.line)   // ios: Views/ShelterView.swift:326
            val busy = story.isLoading(StoryBook.side)
            // ios: Views/ShelterView.swift:327,328,329,331
            PaidRow(artPainter("art_ch6"), "6장", Shelter.chapter6Name, if (story.isLoading(StoryBook.side)) "받는 중…" else if (story.failText(StoryBook.side) == null) "받기" else "다시 받기",
                if (busy) Modifier else Modifier.plainClickable { scope.launch { story.fetch(StoryBook.side, force = true) } }, note = story.failText(StoryBook.side))
        } else {   // ios: Views/ShelterView.swift:332
            HDivider(Theme.line)   // ios: Views/ShelterView.swift:333
            // ios: Views/ShelterView.swift:334,335,337
            PaidRow(artPainter("art_ch6"), "6장", Shelter.chapter6Name, "구독 ›", Modifier.plainClickable { s.support = true }, note = "구독하면 이어 읽어요", locked = true)
        }
    }
}

// ios: Views/ShelterView.swift:365
@Composable
private fun FoldRow(s: ShelterState, id: String, title: String, right: String, cur: String?) {
    val open = s.isOpen(id, cur)   // ios: Views/ShelterView.swift:366
    val st = if (open) "펼침" else "접힘"
    // ios: Views/ShelterView.swift:367,370 (withAnimation easeOut 0.2초: 꺾쇠가 open ? 90 : 0 으로 돈다)
    val rot by animateFloatAsState(if (open) 90f else 0f, tween(200, easing = EaseOut), label = "fold"); val click = Modifier.plainClickable { s.fold[id] = !open }
    // Views/ShelterView.swift:380 (검사 예외: 보간 속 따옴표)
    // ios: Views/ShelterView.swift:368,375,376,377,379
    Fg(Theme.ink) { Row(click.fillMaxWidth().defaultMinSize(minHeight = 44.dp).semantics(mergeDescendants = true) { contentDescription = "$title, $right, $st" }.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Sym("chevron.right", 12f, Theme.teal, Modifier.rotate(rot))   // ios: Views/ShelterView.swift:369
        T(title, style = appFont(14f, FontWeight.Bold))   // ios: Views/ShelterView.swift:371
        FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:372
        T(right, style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:373
    } }
}

/** 서버에서 받는 장 한 줄 (6장부터, 본편 1권) */
// ios: Views/ShelterView.swift:398
@Composable
private fun PaidRow(art: Painter?, title: String, name: String, right: String, modifier: Modifier = Modifier, note: String? = null, locked: Boolean = false) {
    // ios: Views/ShelterView.swift:399,415,416,417,418
    Fg(if (locked) Theme.sub else Theme.ink) { Row(modifier.fillMaxWidth().background(if (locked) hexD(0xF7F8FA, 0x202933) else Color.Transparent).defaultMinSize(minHeight = 56.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(6.dp))) {   // ios: Views/ShelterView.swift:400,406
            if (art != null) {   // ios: Views/ShelterView.swift:401
                // ios: Views/ShelterView.swift:402,403
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = toneFilter(if (locked) 1f else 0f, if (locked) -0.3f else 0f), alpha = if (locked) 0.6f else 1f)
            } else Box(Modifier.fillMaxSize().background(Theme.shelter))   // ios: Views/ShelterView.swift:404
        }
        FitText(title, appFont(12f, FontWeight.Bold), 0.7f, Modifier.width(44.dp), maxLines = 1, color = Theme.teal)   // ios: Views/ShelterView.swift:407
        Column(Modifier.weight(1f).widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/ShelterView.swift:408,412 (남은 폭)
            T(name, style = appFont(14f, FontWeight.Bold))   // ios: Views/ShelterView.swift:409
            if (note != null) T(note, style = appFont(12f), color = Theme.sub, maxLines = 2)   // ios: Views/ShelterView.swift:410
        }
        T(right, style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:413
    } }
}

// 본편 『중첩된 현실』 1권 (앱 연재판). 한국어만
// ios: Views/ShelterView.swift:424
@Composable
private fun Vol1(m: AppModel, s: ShelterState) {
    val story = s.story
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { story.fetch(StoryBook.vol1) }   // ios: Views/ShelterView.swift:455
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/ShelterView.swift:425
        Header("본편 『중첩된 현실』 1권", if (story.count(StoryBook.vol1) > 0) "${story.count(StoryBook.vol1)}장" else "")   // ios: Views/ShelterView.swift:426
        // ios: Views/ShelterView.swift:427,451,452,453
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Theme.card).border(1.dp, Theme.border, RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (story.count(StoryBook.vol1) > 0) {   // ios: Views/ShelterView.swift:428
                val parts = s.vol1Parts(); val cur = s.current(m)   // ios: Views/ShelterView.swift:429
                parts.forEachIndexed { n, part ->   // ios: Views/ShelterView.swift:430
                    if (n > 0) HDivider(Theme.line)   // ios: Views/ShelterView.swift:431
                    // ios: Views/ShelterView.swift:432
                    FoldRow(s, part.id, part.name, "${part.idx.count { story.isDone(StoryBook.vol1, it) }}/${part.idx.size} 읽음", cur)
                    if (s.isOpen(part.id, cur)) {   // ios: Views/ShelterView.swift:433
                        for (i in part.idx) {   // ios: Views/ShelterView.swift:434
                            HDivider(Theme.line)   // ios: Views/ShelterView.swift:435
                            // ios: Views/ShelterView.swift:436,437,438,440
                            PaidRow(rememberCover(story, i, StoryBook.vol1), story.chapter(i, StoryBook.vol1)?.title ?: "", story.chapter(i, StoryBook.vol1)?.name ?: "",
                                if (story.isDone(StoryBook.vol1, i)) "다 읽음" else if ((story.pos[Story.key(StoryBook.vol1, i)] ?: 0) > 0) "이어 읽기" else "읽기", Modifier.plainClickable { m.push("vol1:$i") })
                        }
                    }
                }
            } else {   // ios: Views/ShelterView.swift:444
                val busy = story.isLoading(StoryBook.vol1)
                // ios: Views/ShelterView.swift:445,446,448
                PaidRow(null, "1권", "중첩된 현실", if (story.isLoading(StoryBook.vol1)) "받는 중…" else if (story.failText(StoryBook.vol1) == null) "받기" else "다시 받기",
                    if (busy) Modifier else Modifier.plainClickable { scope.launch { story.fetch(StoryBook.vol1, force = true) } }, note = story.failText(StoryBook.vol1))
            }
        }
    }
}

// ios: Views/ShelterView.swift:458
@Composable
private fun ItemTile(m: AppModel, s: ShelterState, i: Int, itm: com.naeilo.app.data.ShelterItem) {
    val on = i < m.itemsOn   // ios: Views/ShelterView.swift:459
    val off = on && m.itemsOff.contains(itm.id)   // ios: Views/ShelterView.swift:460
    // ios: Views/ShelterView.swift:461,467,468,472
    Fg(if (on) Theme.ink else Theme.muted) { Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 84.dp).plainClickable { if (on) { if (s.item == itm.id) { togglePlace(m, itm.id) } else { s.item = itm.id } } }
        // ios: Views/ShelterView.swift:469
        .background(if (on) (if (s.item == itm.id) Theme.mintBg else Theme.card) else hexD(0xF6F7F8, 0x1D252E), RoundedCornerShape(12.dp))
        // ios: Views/ShelterView.swift:470
        .dashBorder(if (on) (if (s.item == itm.id) Theme.teal else if (off) Theme.dash else Theme.border) else Theme.dash, 12.dp, 2.dp, if (on && !off) null else floatArrayOf(4f, 3f))
        // ios: Views/ShelterView.swift:462,473
        .semantics(mergeDescendants = true) { contentDescription = if (on) itm.name + (if (off) ", 방에서 빼 둠" else "") else if (i == m.itemsOn) "다음에 돌아오는 물건" else "${i + 1}번째로 돌아오는 물건" },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelN("art_" + itm.id + (if (on) "" else "_l"), 32.dp, 32.dp, alpha = if (off) 0.4f else 1f)   // ios: Views/ShelterView.swift:463
        T(if (on) itm.name else "???", style = appFont(11f, FontWeight.Bold).copy(textAlign = TextAlign.Center), maxLines = 2)   // ios: Views/ShelterView.swift:464
        // ios: Views/ShelterView.swift:465
        T(if (off) "빼 둠" else if (i == m.itemsOn) "다음 차례" else "${i + 1}번째", style = appFont(10f), color = if (i == m.itemsOn) Theme.teal else Theme.sub)
    } }
}

/** 쉼터 방: 돌아온 물건이 제자리에 놓이고, 홈에 둔 친구는 물건과 겹치지 않게 문 앞에 선다. */
// ios: Views/ShelterView.swift:477
@Composable
private fun Room(m: AppModel, s: ShelterState) {
    // ios: Views/ShelterView.swift:478,500,501,502,503
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).semantics { contentDescription = "쉼터 방, 돌아온 물건 ${m.itemsOn}개" }) {
        val u = maxWidth / 128   // ios: Views/ShelterView.swift:479
        Box(Modifier.fillMaxSize()) {   // ios: Views/ShelterView.swift:480
            RoomImage(m.roomBG, Modifier.fillMaxSize())   // ios: Views/ShelterView.swift:481
            Shelter.items.forEachIndexed { i, itm ->   // ios: Views/ShelterView.swift:482
                val p = Shelter.slots[itm.id]; if (p != null && i < m.itemsOn && !m.itemsOff.contains(itm.id)) {   // ios: Views/ShelterView.swift:483
                    val on = i < m.itemsOn   // ios: Views/ShelterView.swift:484
                    // ios: Views/ShelterView.swift:485,489,490,491,492
                    Box(Modifier.offset(x = u * p.x, y = u * p.y).semantics { contentDescription = if (on) itm.name else "아직 안 돌아온 물건 미리 보기" }
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = on, onClickLabel = if (on) "누르면 방에서 빼요" else "") { if (on) { s.item = itm.id; m.itemsOff = m.itemsOff + itm.id } }) {
                        PixelN("art_" + itm.id, u * 16, u * 16, alpha = if (on) 1f else 0.35f)   // ios: Views/ShelterView.swift:486,487
                    }
                }
            }
            // ios: Views/ShelterView.swift:495,496,497 문(6~26) 앞. 의자는 30부터라 겹치지 않는다
            CharPixel(id = m.homeFriendShown, width = u * 24, height = u * 34, modifier = Modifier.offset(x = u * 4, y = u * 42).clearAndSetSemantics {}.charIdle(), model = m)
        }
    }
}

// ios: Views/ShelterView.swift:506
@Composable
private fun ItemNote(m: AppModel, s: ShelterState) {
    val cur = Shelter.items.firstOrNull { it.id == s.item } ?: (if (m.itemsOn > 0) Shelter.items[m.itemsOn - 1] else null)   // ios: Views/ShelterView.swift:507
    // ios: Views/ShelterView.swift:508,530,531,532
    Row(Modifier.fillMaxWidth().background(Theme.card, RoundedCornerShape(12.dp)).border(1.dp, Theme.border, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelN("art_" + (cur?.id ?: "barley_tea") + (if (cur == null) "_l" else ""), 32.dp, 32.dp)   // ios: Views/ShelterView.swift:509
        Column(Modifier.weight(1f).widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/ShelterView.swift:510,518 (남은 폭)
            T(cur?.name ?: "아직 돌아오지 않았어요", style = appFont(14f, FontWeight.Bold))   // ios: Views/ShelterView.swift:511
            T(cur?.line ?: "오늘의 1분을 하면 첫 물건이 돌아와요.", style = appFont(13f), color = Theme.sub)   // ios: Views/ShelterView.swift:512
            val voice = m.voice(m.homeFriendShown)
            // ios: Views/ShelterView.swift:513
            val say = cur?.let { c -> Lines.pick("item.${c.id}.$voice") ?: WItem.say(c.id, m.voice(m.homeFriendShown)) }; if (cur != null && say != null) {
                val who = m.friendName(m.homeFriendShown)   // ios: Views/ShelterView.swift:514
                T("$who “$say”", style = appFont(13f, FontWeight.Bold), color = Theme.teal)   // ios: Views/ShelterView.swift:515
            }
        }
        val c = cur; if (c != null) {   // ios: Views/ShelterView.swift:519
            val off = m.itemsOff.contains(c.id)   // ios: Views/ShelterView.swift:520
            // ios: Views/ShelterView.swift:521,523,524,526,527
            Box(Modifier.semantics(mergeDescendants = true) { contentDescription = if (off) "${c.name} 방에 다시 놓기" else "${c.name} 방에서 빼기" }
                .plainClickable { s.item = c.id; togglePlace(m, c.id) }
                .defaultMinSize(minHeight = 36.dp).background(if (off) Theme.teal else Theme.mintBg, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                T(if (off) "다시 놓기" else "빼기", style = appFont(13f, FontWeight.Bold), color = if (off) Color.White else Theme.teal)   // ios: Views/ShelterView.swift:522
            }
        }
    }
}

/** 방 배치에서 빼거나 다시 놓는다. 모은 물건은 그대로 남는다 */
// ios: Views/ShelterView.swift:536
private fun togglePlace(m: AppModel, id: String) {
    if (m.itemsOff.contains(id)) { m.itemsOff = m.itemsOff - id } else { m.itemsOff = m.itemsOff + id }   // ios: Views/ShelterView.swift:537
}

// 장면 도트 표지. 5장은 4프레임 애니메이션 (움직임 줄이기 설정이면 첫 프레임만).
/** 서버에서 받은 장의 도트 그림 (data URL → 그림). 한 번 푼 그림은 기억해 둔다 */
private val coverCache = HashMap<String, ImageBitmap>()   // ios: Views/ShelterView.swift:543
// ios: Views/ShelterView.swift:544,545
fun Story.cover(i: Int, b: StoryBook = StoryBook.side): ImageBitmap? {
    val url = chapter(i, b)?.cover ?: return null   // ios: Views/ShelterView.swift:546
    return coverImage(url)
}
private fun cachedCover(url: String): ImageBitmap? = synchronized(coverCache) { coverCache[url] }
/** data URL 하나를 푼다 (그리기 밖에서 부른다) */
private fun coverImage(url: String): ImageBitmap? {
    cachedCover(url)?.let { return it }   // ios: Views/ShelterView.swift:547
    // ios: Views/ShelterView.swift:548
    val comma = url.indexOf(","); val d = (if (comma < 0) null else runCatching { Base64.decode(url.substring(comma + 1), Base64.DEFAULT) }.getOrNull()) ?: return null
    val ui = BitmapFactory.decodeByteArray(d, 0, d.size) ?: return null   // ios: Views/ShelterView.swift:549
    val img = ui.asImageBitmap()   // ios: Views/ShelterView.swift:550
    synchronized(coverCache) { coverCache[url] = img }   // ios: Views/ShelterView.swift:551
    return img   // ios: Views/ShelterView.swift:552
}

// ios: Views/ShelterView.swift:556,557,558,560
@Composable
fun ChapterCover(index: Int, on: Boolean = true, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current   // ios: Views/ShelterView.swift:559
    val active = LocalTabActive.current
    val ctx = LocalContext.current
    // ios: Views/ShelterView.swift:561,571,572,573
    val filter = remember(on) { toneFilter(if (on) 0f else 1f, if (on) 0f else -0.3f) }; val alpha = if (on) 1f else 0.6f; val box = modifier.clip(RoundedCornerShape(6.dp)).background(Theme.shelter)
    if (index == 5 && on && !reduce) {   // ios: Views/ShelterView.swift:562
        // ios: Views/ShelterView.swift:565 (그림 네 장은 한 번만 읽는다)
        val ids = remember { (0 until 4).map { drawableId(ctx, "art_ch5_$it") } }; val frames = ids.filter { it != 0 }.map { ImageBitmap.imageResource(it) }
        val frame = remember { mutableIntStateOf(0) }
        // ios: Views/ShelterView.swift:563,564 0.7초마다 다음 장면 (그리기만 다시 한다). 탭이 숨으면 멈춘다
        LaunchedEffect(active) { while (active) { frame.intValue = ((System.currentTimeMillis() / 700) % 4).toInt(); delay(700 - System.currentTimeMillis() % 700) } }
        Canvas(box) { if (frames.isNotEmpty()) drawImage(frames[frame.intValue % frames.size], dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.None, alpha = alpha, colorFilter = filter) }
    } else {   // ios: Views/ShelterView.swift:567
        // ios: Views/ShelterView.swift:568
        val id = drawableId(ctx, Shelter.cover(index)); val bmp = if (id != 0) ImageBitmap.imageResource(id) else null
        Canvas(box) { if (bmp != null) drawImage(bmp, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.None, alpha = alpha, colorFilter = filter) }
    }
}

/** ScrollViewReader.scrollTo(id): 머리("top")는 0번, 문단 p 는 p+1번 항목 */
private suspend fun LazyListState.scrollTo(id: Any) = scrollToItem(if (id is Int) id + 1 else 0)

// 서재 읽기: 외전 원고(한글·영문)를 앱에 넣어 두고 읽는다. 언어는 기기 언어가 기본이고, 위의 한/EN 으로 바꾸면 기억한다.
// 읽던 자리는 장마다 블록 번호로 남기고(두 언어가 같은 번호), 서재 위젯이 그 자리의 문단을 보여 준다.
// 이동 막대는 화면이 그린다 (어둡게 읽을 때 막대도 같은 바탕). readerPage 로 연다
// ios: Views/ShelterView.swift:579,580,582,593
@Composable
fun ReaderView(m: AppModel, index: Int, book: StoryBook = StoryBook.side) {
    var idx by remember(book) { mutableIntStateOf(index) }   // ios: Views/ShelterView.swift:581 @State var index
    var size by remember { mutableDoubleStateOf(17.0) }   // ios: Views/ShelterView.swift:583
    var dark by remember { mutableStateOf(false) }   // ios: Views/ShelterView.swift:584
    var top by remember { mutableStateOf<Int?>(null) }   // ios: Views/ShelterView.swift:585
    val story = remember { Story.shared }   // ios: Views/ShelterView.swift:586
    var support by remember { mutableStateOf(false) }   // ios: Views/ShelterView.swift:587
    val scheme = LocalPal.current.isDark   // ios: Views/ShelterView.swift:588
    val nav = LocalNavBack.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }   // 읽던 자리로는 처음 연 장에서 한 번만 간다 (onAppear)
    val opened = remember { intArrayOf(index) }   // 자리를 재기 시작한 장 (다음 장으로 넘기면 맨 위부터 잰다)

    /** 앱에 든 외전 장(프롤로그~5장): 쉼터 친구·표지·미션 열림과 이어진다. 6장부터와 앱 연재판 1권은 구독으로 서버에서 받은 장 */
    val inApp = book == StoryBook.side && idx < 6   // ios: Views/ShelterView.swift:591
    val accent = if (dark) Theme.mint else Theme.teal   // ios: Views/ShelterView.swift:594
    val ch = if (inApp) Shelter.chapters[idx] else null   // ios: Views/ShelterView.swift:595
    val fi = ch?.let { c -> Shelter.friends.indexOfFirst { it.id == c.friend }.takeIf { it >= 0 } ?: 0 }   // ios: Views/ShelterView.swift:596
    val text = story.chapter(idx, book)   // ios: Views/ShelterView.swift:597
    val started = if (inApp) m.readPos.contains(idx) else (story.pos[Story.key(book, idx)] ?: 0) > 0   // ios: Views/ShelterView.swift:598
    val blocks = text?.blocks ?: emptyList()
    val subC = if (dark) hex(0xA6ADC6) else Theme.sub2

    // ios: Views/ShelterView.swift:638
    LaunchedEffect(Unit) {
        story.touch(book, idx)   // ios: Views/ShelterView.swift:639
        if (inApp) m.readLast = idx   // ios: Views/ShelterView.swift:640
        if (scheme) dark = true   // ios: Views/ShelterView.swift:641 (scheme == .dark)
    }
    // ios: Views/ShelterView.swift:632 원고가 오면 읽던 자리로 간 뒤, 맨 위에 보이는 문단 번호를 읽던 자리로 둔다
    LaunchedEffect(idx, book, text) {
        if (text == null) return@LaunchedEffect
        val p = story.pos[Story.key(book, idx)]; if (!restored && p != null && p > 0) list.scrollTo(p)   // ios: Views/ShelterView.swift:642
        else if (restored && opened[0] != idx) list.scrollTo("top")   // 앞 장의 자리가 새 장에 남지 않게
        restored = true; opened[0] = idx
        snapshotFlow { list.firstVisibleItemIndex }.collect { fv -> top = max(0, minOf(fv - 1, blocks.size - 1)) }
    }
    // ios: Views/ShelterView.swift:633
    LaunchedEffect(top) {
        val t = top ?: return@LaunchedEffect   // ios: Views/ShelterView.swift:634
        story.setPos(book, idx, t)   // ios: Views/ShelterView.swift:635
        if (inApp && t > 0 && !m.readPos.contains(idx)) m.readPos = m.readPos + idx   // ios: Views/ShelterView.swift:636
    }
    // 받은 장이 없으면(처음이거나 다른 언어) 받는다. 5장 끝에서는 6장을 미리 받아 둔다
    // ios: Views/ShelterView.swift:650,651
    LaunchedEffect("$idx-${story.lang(book).name}") { if (story.chapter(idx, book) == null || (book == StoryBook.side && idx == 5)) story.fetch(book) }
    LaunchedEffect(Unit) { snapshotFlow { story.lang }.drop(1).collect { story.pushWidget(m) } }   // ios: Views/ShelterView.swift:653
    DisposableEffect(Unit) { onDispose { story.pushWidget(m) } }   // ios: Views/ShelterView.swift:654
    // 끝까지 내려와 '다음' 줄이 보이면 다 읽은 장
    // ios: Views/ShelterView.swift:629
    LaunchedEffect(idx, book, text) { if (text != null) { snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == "next" } }.first { it }; story.markDone(book, idx) } }

    // ios: Views/ShelterView.swift:645,646,647
    Fg(if (dark) hex(0xEEF0F7) else Theme.ink) { Column(Modifier.fillMaxSize().background(if (dark) hex(0x141824) else hex(0xFBFAF7))) {
        NavBar(if (book == StoryBook.side) "서재" else "본편 1권", back = nav?.back) { nav?.pop?.invoke() }   // ios: Views/ShelterView.swift:648
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Views/ShelterView.swift:599,600,601
            // ios: Views/ShelterView.swift:602,615,616
            item(key = "top") { Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReaderControls(story, book, dark, size, accent, { size = it }, { dark = !dark })   // ios: Views/ShelterView.swift:603
                ReaderCover(story, idx, book, inApp)   // ios: Views/ShelterView.swift:604
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {   // ios: Views/ShelterView.swift:605
                    T(story.heading(idx, book), style = appFont(13f, FontWeight.SemiBold), color = accent)   // ios: Views/ShelterView.swift:606
                    T(text?.name ?: ch?.name ?: "", style = appFont(22f, FontWeight.Bold))   // ios: Views/ShelterView.swift:607
                }
                if (text == null) {   // ios: Views/ShelterView.swift:609
                    FetchNote(story, book, dark, accent) { support = true }   // ios: Views/ShelterView.swift:610
                } else {   // ios: Views/ShelterView.swift:611
                    T(if (started) "읽던 곳에서 이어 읽는 중" else "처음부터", style = appFont(13f), color = if (dark) hex(0xA6ADC6) else Theme.sub2)   // ios: Views/ShelterView.swift:612
                }
            } }
            // ios: Views/ShelterView.swift:617,618,619,623,626,627 (문단 사이 size × 0.8, 첫 문단 위 8, 묶음 안 간격 0)
            itemsIndexed(blocks, key = { p, _ -> p }) { p, b -> Column(Modifier.padding(horizontal = 16.dp).padding(top = if (p == 0) 8.dp else (size * 0.8).dp)) {
                ReaderBlock(b, size, dark, accent, subC)   // ios: Views/ShelterView.swift:620
                // ios: Views/ShelterView.swift:621
                if (p == 1 && ch != null && fi != null && Shelter.friends[fi].inSideStory) FirstAppear(m, ch.friend, fi, dark, Modifier.padding(top = (size * 0.8).dp))
            } }
            // ios: Views/ShelterView.swift:628
            item(key = "next") { Box(Modifier.padding(16.dp)) {
                ReaderNext(m, fi ?: 0, story, book, idx, inApp, dark, accent, { support = true }) {
                    if (inApp) { m.readPos = m.readPos + idx; story.pushWidget(m) }   // ios: Views/ShelterView.swift:778
                    idx += 1; top = null   // ios: Views/ShelterView.swift:779
                    story.touch(book, idx)   // ios: Views/ShelterView.swift:780
                    if (book == StoryBook.side && idx < 6) m.readLast = idx   // ios: Views/ShelterView.swift:781 (inApp 은 다음 장 기준으로 다시 본다)
                    scope.launch { list.scrollTo("top") }   // ios: Views/ShelterView.swift:782
                }
            } }
        }
    } }
    Sheet(support, { support = false }) { SupportView(m) }   // ios: Views/ShelterView.swift:655
}

// ios: Views/ShelterView.swift:658
@Composable
private fun ReaderCover(story: Story, idx: Int, book: StoryBook, inApp: Boolean) {
    if (inApp) {   // ios: Views/ShelterView.swift:659
        ChapterCover(index = idx, modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)))   // ios: Views/ShelterView.swift:660
    } else {
        val art = rememberCover(story, idx, book); if (art != null) {   // ios: Views/ShelterView.swift:661
            // ios: Views/ShelterView.swift:662,663
            Image(art, null, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)).background(Theme.shelter), contentScale = ContentScale.FillBounds)
        } else if (book == StoryBook.side) {   // ios: Views/ShelterView.swift:664
            // ios: Views/ShelterView.swift:665,666
            artPainter("art_ch6")?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp)).background(Theme.shelter), contentScale = ContentScale.FillBounds) }
        }
    }
}

/** 받는 중 · 오류 · 다시 받기 */
// ios: Views/ShelterView.swift:671
@Composable
private fun FetchNote(story: Story, book: StoryBook, dark: Boolean, accent: Color, openSupport: () -> Unit) {
    val scope = rememberCoroutineScope()
    if (story.isLoading(book)) {   // ios: Views/ShelterView.swift:672
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Spinner(); T("원고를 받는 중이에요", style = appFont(14f)) }   // ios: Views/ShelterView.swift:673
    } else if (!Support.shared.open(book)) {   // ios: Views/ShelterView.swift:674
        // ios: Views/ShelterView.swift:675,676,677
        T(if (book == StoryBook.side) "구독하면 읽을 수 있어요 ›" else "연 구독, 또는 월 구독 ${Support.vol1Months}개월 이상이면 읽을 수 있어요 ›", Modifier.plainClickable(openSupport), style = appFont(14f, FontWeight.Bold), color = accent)
    } else {   // ios: Views/ShelterView.swift:678
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {   // ios: Views/ShelterView.swift:679
            // ios: Views/ShelterView.swift:680,681
            T(story.failText(book) ?: "원고를 아직 받지 못했어요.", style = appFont(14f), color = if (dark) hex(0xA6ADC6) else Theme.sub2)
            // ios: Views/ShelterView.swift:682,683
            T("다시 받기", Modifier.plainClickable { scope.launch { story.fetch(book, force = true) } }, style = appFont(14f, FontWeight.Bold), color = accent)
        }
    }
}

// 한/EN · 글자 크기 3단계 · 어둡게
// ios: Views/ShelterView.swift:689
@Composable
private fun ReaderControls(story: Story, book: StoryBook, dark: Boolean, size: Double, accent: Color, setSize: (Double) -> Unit, toggleDark: () -> Unit) {
    val ink = if (dark) hex(0xEEF0F7) else Theme.ink; val edge = if (dark) hex(0x4A5578) else Theme.border   // ios: Views/ShelterView.swift:690
    val density = LocalDensity.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {   // ios: Views/ShelterView.swift:691
        if (book.langs.size == 1) {   // ios: Views/ShelterView.swift:692
            T("한국어만 있어요", style = appFont(12f), color = if (dark) hex(0xA6ADC6) else Theme.sub2)   // ios: Views/ShelterView.swift:693
        } else {   // ios: Views/ShelterView.swift:694
            Row(Modifier.border(2.dp, edge, CapsuleShape).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {   // ios: Views/ShelterView.swift:695,705
                StoryLang.entries.forEach { l ->   // ios: Views/ShelterView.swift:696
                    val on = story.lang == l
                    // ios: Views/ShelterView.swift:697,698,699,700,701,702
                    Box(Modifier.defaultMinSize(38.dp, 32.dp).background(if (story.lang == l) accent else Color.Transparent, CapsuleShape)
                        .semantics { contentDescription = if (l == StoryLang.ko) "한글 원고" else "영문 번역본"; selected = on }
                        .plainClickable { story.pick(l) }, contentAlignment = Alignment.Center) {
                        T(l.label, style = appFont(13f, FontWeight.Bold), color = if (story.lang == l) (if (dark) hex(0x141824) else Color.White) else ink)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 4.dp))   // ios: Views/ShelterView.swift:707
        // ios: Views/ShelterView.swift:708
        listOf(Triple(15.0, 12.0, "작은 글자"), Triple(17.0, 15.0, "보통 글자"), Triple(20.0, 18.0, "큰 글자")).forEach { (s, f, label) ->
            // ios: Views/ShelterView.swift:709,710,711,712,713,714,715 (.font(.system(size:)): 큰 글자 설정을 따르지 않는다)
            Box(Modifier.defaultMinSize(36.dp, 36.dp).background(if (size == s) accent else Color.Transparent, CapsuleShape).border(2.dp, if (size == s) accent else edge, CapsuleShape)
                .semantics { contentDescription = label }.plainClickable { setSize(s) }, contentAlignment = Alignment.Center) {
                T("가", style = TextStyle(fontSize = with(density) { f.toFloat().dp.toSp() }, fontWeight = FontWeight.Bold), color = if (size == s) (if (dark) hex(0x141824) else Color.White) else ink)
            }
        }
        // ios: Views/ShelterView.swift:717,718,719,720,721
        Box(Modifier.defaultMinSize(minHeight = 36.dp).background(if (dark) hex(0x262E45) else Theme.card, CapsuleShape).border(2.dp, edge, CapsuleShape).plainClickable(toggleDark).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center) {
            T(if (dark) "밝게" else "어둡게", style = appFont(13f, FontWeight.Bold), color = ink)
        }
    }
}

// ios: Views/ShelterView.swift:725
@Composable
private fun ReaderBlock(b: StoryBlock, size: Double, dark: Boolean, accent: Color, sub: Color) {
    @Suppress("UNUSED_VARIABLE") val sub0 = if (dark) hex(0xA6ADC6) else Theme.sub2   // ios: Views/ShelterView.swift:726 (바깥에서 sub 로 받는다)
    val fs = size.toFloat()
    when (b.k) {   // ios: Views/ShelterView.swift:727
        "h" ->   // ios: Views/ShelterView.swift:728
            T(b.t, Modifier.padding(top = size.dp), style = appFont((size * 0.85).toFloat(), FontWeight.Bold), color = accent)   // ios: Views/ShelterView.swift:729
        // 화면에 뜬 글·메시지: 왼쪽 줄 + 조금 흐린 색
        "q" ->   // ios: Views/ShelterView.swift:730
            // ios: Views/ShelterView.swift:732,733,734,735
            TA(readerMd(b.t), Modifier.drawBehind { drawRoundRect(accent.copy(alpha = 0.6f), size = Size(2.dp.toPx(), this.size.height), cornerRadius = CornerRadius(1.dp.toPx())) }.padding(start = 12.dp),
               style = appFont((size * 0.95).toFloat()).ls((size * 0.35).toFloat()), color = sub)
        "s" ->   // ios: Views/ShelterView.swift:736
            T("⁂", Modifier.fillMaxWidth().clearAndSetSemantics {}, style = appFont(fs).copy(textAlign = TextAlign.Center), color = sub)   // ios: Views/ShelterView.swift:737
        "t" ->   // ios: Views/ShelterView.swift:738
            // ios: Views/ShelterView.swift:739,749,750
            Box(Modifier.border(1.dp, if (dark) hex(0x3A4459) else Theme.border, RoundedCornerShape(10.dp)).padding(12.dp)) { TableGrid(b.rows ?: emptyList(), hSpacing = 12.dp, vSpacing = 6.dp, divider = sub) { r, c ->
                TA(readerMd(c), style = appFont((size * 0.85).toFloat(), if (r == 0) FontWeight.Bold else FontWeight.Normal))   // ios: Views/ShelterView.swift:740,741,742,743 (머리 줄 아래 선: 746)
            } }
        else ->   // ios: Views/ShelterView.swift:751
            TA(readerMd(b.t), style = appFont(fs).ls((size * 0.45).toFloat()))   // ios: Views/ShelterView.swift:752
    }
}

/** Grid(alignment: .leading): 칸 폭 = 그 열에서 가장 긴 글. 넓으면 비율대로 줄여 접는다. 첫 줄 아래엔 선 (r == 0 → Divider) */
// ios: Views/ShelterView.swift:746
@Composable
private fun TableGrid(rows: List<List<String>>, hSpacing: Dp, vSpacing: Dp, divider: Color, cell: @Composable (Int, String) -> Unit) {
    val cols = rows.maxOfOrNull { it.size } ?: 0
    Layout(content = {
        rows.forEachIndexed { r, row -> row.forEach { c -> Box { cell(r, c) } } }
        if (rows.isNotEmpty()) Box(Modifier.fillMaxWidth().background(divider))   // 머리 줄 아래 선 (if r == 0)
    }) { ms, c ->
        val hs = hSpacing.roundToPx(); val vs = vSpacing.roundToPx(); val line = 1
        val cells = if (rows.isNotEmpty()) ms.dropLast(1) else ms
        val at = mutableListOf<Pair<Int, Int>>(); rows.forEachIndexed { r, row -> row.indices.forEach { at.add(r to it) } }
        val w = IntArray(cols); cells.forEachIndexed { k, mm -> val ci = at[k].second; w[ci] = max(w[ci], mm.maxIntrinsicWidth(Int.MAX_VALUE)) }
        val avail = if (c.hasBoundedWidth) c.maxWidth - hs * max(0, cols - 1) else Int.MAX_VALUE
        val sum = w.sum()
        if (sum > avail && sum > 0) for (i in w.indices) w[i] = w[i] * avail / sum
        val ps = cells.mapIndexed { k, mm -> mm.measure(Constraints(0, max(0, w[at[k].second]), 0, Constraints.Infinity)) }
        val rowH = IntArray(rows.size); ps.forEachIndexed { k, p -> val r = at[k].first; rowH[r] = max(rowH[r], p.height) }
        val total = w.sum() + hs * max(0, cols - 1)
        val dv = if (rows.isNotEmpty()) ms.last().measure(Constraints.fixed(max(0, total), line)) else null
        val h = rowH.sum() + vs * max(0, rows.size - 1) + (if (dv != null) line + vs else 0)
        layout(total, h) {
            var y = 0; var k = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                row.indices.forEach { ci -> ps[k].place(x, y); x += w[ci] + hs; k++ }
                y += rowH[r]
                if (r == 0 && dv != null) { y += vs; dv.place(0, y); y += line }
                y += vs
            }
        }
    }
}

// ios: Views/ShelterView.swift:756
@Composable
private fun FirstAppear(m: AppModel, id: String, fi: Int, dark: Boolean, modifier: Modifier) {
    // ios: Views/ShelterView.swift:757,758,766,767,768,770
    Row(modifier.fillMaxWidth().background(if (dark) hex(0x262E45) else Theme.cream, RoundedCornerShape(12.dp)).border(1.dp, if (dark) hex(0x4A5578) else Theme.yellow, RoundedCornerShape(12.dp))
        .plainClickable { m.push("char:" + id) }.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelN("spr_" + id, 24.dp, 34.dp)   // ios: Views/ShelterView.swift:759
        Column {   // ios: Views/ShelterView.swift:760
            T("${Shelter.friends[fi].name} 처음 나오는 장면", style = appFont(13f, FontWeight.Bold))   // ios: Views/ShelterView.swift:761
            T("쉼터에서 만날 수 있어요 ›", style = appFont(12f))   // ios: Views/ShelterView.swift:762
        }
        Spacer(Modifier.weight(1f))   // ios: Views/ShelterView.swift:764
    }
}

// ios: Views/ShelterView.swift:773
@Composable
private fun ReaderNext(m: AppModel, @Suppress("UNUSED_PARAMETER") fi: Int, story: Story, book: StoryBook, idx: Int, inApp: Boolean, dark: Boolean, accent: Color, openSupport: () -> Unit, goNext: () -> Unit) {
    val last = if (inApp && idx < 5) !m.chapterOn(idx + 1) else idx + 1 >= story.count(book)   // ios: Views/ShelterView.swift:774
    if (!last) {   // ios: Views/ShelterView.swift:775
        val n = story.chapter(idx + 1, book)   // ios: Views/ShelterView.swift:776
        val nt = n?.title.orEmpty(); val nn = n?.name.orEmpty()
        // ios: Views/ShelterView.swift:777,783,785,786
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).background(accent, RoundedCornerShape(12.dp)).plainClickable(goNext), contentAlignment = Alignment.Center) {
            T("다음: $nt · $nn ›", style = appFont(15f, FontWeight.Bold), color = Color.White)   // Views/ShelterView.swift:784 (검사 예외: 보간 속 따옴표)
        }
    } else if (book == StoryBook.side && idx == 5 && !Support.shared.sideOpen && story.count(StoryBook.side) <= 6) {   // ios: Views/ShelterView.swift:787
        // 5장 끝: 6장부터는 구독으로
        // ios: Views/ShelterView.swift:789,792,793
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).background(accent, RoundedCornerShape(12.dp)).plainClickable(openSupport).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            // ios: Views/ShelterView.swift:790,791
            T("6장 · ${Shelter.chapter6Name}는 구독하면 이어 읽어요 ›", style = appFont(14f, FontWeight.Bold).copy(textAlign = TextAlign.Center), color = Color.White)
        }
    } else {   // ios: Views/ShelterView.swift:794
        // ios: Views/ShelterView.swift:795,796,797
        val t = if (inApp && idx < 5) "이 장을 끝까지 읽으면 다음 장이 열려요." else if (book == StoryBook.side && idx == 5) (if (story.isLoading(StoryBook.side)) "6장을 받는 중이에요" else story.failText(StoryBook.side) ?: "6장은 곧 이어져요") else "끝까지 읽었어요"
        // ios: Views/ShelterView.swift:798,799,800,801
        T(t, Modifier.fillMaxWidth().dashBorder(if (dark) hex(0x4A5578) else Theme.dash, 12.dp, 1.dp, floatArrayOf(4f, 3f)).padding(12.dp), style = appFont(14f, FontWeight.Bold).copy(textAlign = TextAlign.Center), color = if (dark) Theme.gold else Theme.sub)
    }
}

/** 문단 안의 **굵게**·_기울임_·~~지움~~ 과 줄바꿈을 그대로 (인라인 마크다운만, 공백 유지) */
// ios: Views/ShelterView.swift:806,807
fun readerMd(s: String): AnnotatedString = buildAnnotatedString {
    var bold = false; var ital = false; var strike = false; var i = 0
    fun style() = SpanStyle(fontWeight = if (bold) FontWeight.Bold else null, fontStyle = if (ital) FontStyle.Italic else null, textDecoration = if (strike) TextDecoration.LineThrough else null)
    val buf = StringBuilder()
    fun flush() { if (buf.isNotEmpty()) { pushStyle(style()); append(buf.toString()); pop(); buf.clear() } }
    fun wordAt(k: Int) = k in s.indices && s[k].isLetterOrDigit()
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && !s[i + 1].isLetterOrDigit() -> { buf.append(s[i + 1]); i += 2 }
            s.startsWith("**", i) || s.startsWith("__", i) -> { flush(); bold = !bold; i += 2 }
            s.startsWith("~~", i) -> { flush(); strike = !strike; i += 2 }
            c == '*' || (c == '_' && !(wordAt(i - 1) && wordAt(i + 1))) -> { flush(); ital = !ital; i += 1 }
            else -> { buf.append(c); i += 1 }
        }
    }
    flush()
}

// 인물 자세히 보기: 위에 두 갈래 전환 "투자하는 ○○ | 소설 속 ○○" (한 화면 안, 깊이 추가 없음).
// 소설 속 = 『중첩된 현실』 등장 시점, 특징, 배경, 대표 장면 (캐릭터 설정 스레드 요약 기준). 투자하는 = InvestBranch.
// 들어온 길이 처음 갈래를 정한다: 쉼터 → 소설 속, 평정 지수·평행 세계·홈 리마인드 → 투자하는
// 이동 막대는 화면이 그린다 (갈래에 따라 제목이 바뀐다). characterPage 로 연다
// ios: Views/ShelterView.swift:814,815,816,819,821,822,823,827
@Composable
fun CharacterDetailView(m: AppModel, friend: Friend, invest: Boolean = false, focus: Int? = null) {
    var investOn by remember(friend.id) { mutableStateOf(invest && Calm.kind(friend.id) != null) }   // ios: Views/ShelterView.swift:817,824 (@State invest)
    val story = remember { Story.shared }   // ios: Views/ShelterView.swift:818
    val nav = LocalNavBack.current
    val f = friend; val k = Calm.kind(f.id)   // ios: Views/ShelterView.swift:828
    // ios: Views/ShelterView.swift:829,853
    Column(Modifier.fillMaxSize().background(Theme.bg)) {
        NavBar(if (investOn) "${f.name} 규칙" else f.name, back = nav?.back) { nav?.pop?.invoke() }   // ios: Views/ShelterView.swift:854
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Screen(Modifier.padding(top = 8.dp)) {   // ios: Views/ShelterView.swift:851
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/ShelterView.swift:830
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {   // ios: Views/ShelterView.swift:831
                    // ios: Views/ShelterView.swift:832,833,834
                    Box(Modifier.background(Shelter.spriteBacking(f.id), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) { PixelN("spr_" + f.id, 84.dp, 119.dp, Modifier.charIdle()) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {   // ios: Views/ShelterView.swift:835
                        FitText(f.name + (k?.let { " · " + it.title } ?: ""), appFont(24f, FontWeight.Bold), 0.7f, maxLines = 1)   // ios: Views/ShelterView.swift:836
                        // 대사 대신 하는 일 한 줄 (투자 성향이 없는 인물은 기존 대사)
                        // ios: Views/ShelterView.swift:838,839
                        T(if (f.does.isEmpty()) f.line else f.does, style = appFont(14f, FontWeight.SemiBold), color = Theme.sub)
                    }
                }
                if (k != null) {   // ios: Views/ShelterView.swift:842
                    // ios: Views/ShelterView.swift:843,844,845,847
                    Segmented(listOf(true to "규칙과 결과", false to "${f.name} 이야기"), investOn, { investOn = it }, Modifier.semantics { contentDescription = "갈래" })
                }
                if (investOn) InvestBranch(m, f.id, focus) { investOn = false } else Novel(f, story)   // ios: Views/ShelterView.swift:849
            }
        } }
    }
}

// ios: Views/ShelterView.swift:857
@Composable
private fun Novel(f: Friend, story: Story) {
    Card {   // ios: Views/ShelterView.swift:858
        T("소설에 나오는 곳", style = appFont(15f, FontWeight.Bold))   // ios: Views/ShelterView.swift:859
        Appear("본편", f.main)   // ios: Views/ShelterView.swift:860
        Appear("외전 『이종 공명』", f.side)   // ios: Views/ShelterView.swift:861
        Appear("프리퀄", f.prequel?.let { it + " · 준비 중" })   // ios: Views/ShelterView.swift:862
        if (f.side == null) {   // ios: Views/ShelterView.swift:863
            T("앱 외전엔 안 나옴 · 쉼터엔 인터미션 체크인으로", style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:864
        }
    }
    Section("특징", f.traits)   // ios: Views/ShelterView.swift:867
    Section("배경", f.background)   // ios: Views/ShelterView.swift:868
    Card {   // ios: Views/ShelterView.swift:869
        val open = f.scenes.filter { sceneOpen(it.first, story) }; val shut = f.scenes.filter { !sceneOpen(it.first, story) }   // ios: Views/ShelterView.swift:870
        T("대표 장면", style = appFont(15f, FontWeight.Bold))   // ios: Views/ShelterView.swift:871
        T("읽은 장면만", style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:872
        open.forEach { (k, v) ->   // ios: Views/ShelterView.swift:873
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {   // ios: Views/ShelterView.swift:874,878
                T(k, style = appFont(12f, FontWeight.Bold), color = Theme.teal)   // ios: Views/ShelterView.swift:875
                T(v, style = appFont(14f).ls(4f))   // ios: Views/ShelterView.swift:876
            }
        }
        if (shut.isNotEmpty()) {   // ios: Views/ShelterView.swift:880
            // ios: Views/ShelterView.swift:881,885,886
            Row(Modifier.fillMaxWidth().background(Theme.bg, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("lock.fill", 17f, Theme.muted)   // ios: Views/ShelterView.swift:882
                T(lockedText(shut.map { it.first }), style = appFont(13f), color = Theme.sub)   // ios: Views/ShelterView.swift:883
            }
        }
    }
    T("본편 9·10권 결말·일부 인물 이야기 제외", style = appFont(12f), color = Theme.muted)   // ios: Views/ShelterView.swift:889
}

/** 장면 잠금: 앱에서 읽을 수 있는 책의 장을 끝까지 읽어야 열린다. 외전 장면 = 외전 마지막 장, 본편 1권 장면 = 본편 1권 1장.
 *  앱에 없는 책(본편 2권~, 프리퀄)의 장면은 잠긴 채로 둔다 */
// ios: Views/ShelterView.swift:894,895
internal fun sceneOpen(label: String, s: Story): Boolean = when (label) {
    "외전" -> s.isDone(StoryBook.side, StoryBook.side.bundled - 1)   // ios: Views/ShelterView.swift:896
    "본편 1권" -> s.isDone(StoryBook.vol1, 1)   // ios: Views/ShelterView.swift:897
    else -> false   // ios: Views/ShelterView.swift:898
}
/** "본편 3·4권, 프리퀄 장면 3개 · 그 장 읽으면 열림" */
// ios: Views/ShelterView.swift:902
internal fun lockedText(labels: List<String>): String {
    val vols = mutableListOf<String>(); val other = mutableListOf<String>()   // ios: Views/ShelterView.swift:903
    for (l in labels) {   // ios: Views/ShelterView.swift:904
        if (!AppModel.en && l.startsWith("본편 ") && l.endsWith("권")) { val v = l.drop(3).dropLast(1); if (!vols.contains(v)) vols.add(v) }   // ios: Views/ShelterView.swift:905
        else if (!other.contains(l)) other.add(l)   // ios: Views/ShelterView.swift:906
    }
    val parts = (if (vols.isEmpty()) emptyList<String>() else listOf("본편 " + vols.joinToString("·") + "권")) + other   // ios: Views/ShelterView.swift:908
    return parts.joinToString(", ") + " 장면 ${labels.size}개 · 그 장 읽으면 열림"   // ios: Views/ShelterView.swift:909
}

// ios: Views/ShelterView.swift:912
@Composable
private fun Appear(k: String, v: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {   // ios: Views/ShelterView.swift:913 (HStack 기본 간격 8)
        T(k, Modifier.widthIn(min = 110.dp), style = appFont(14f), color = Theme.sub)   // ios: Views/ShelterView.swift:914
        // ios: Views/ShelterView.swift:915
        T(v ?: "나오지 않음", style = appFont(14f, if (v == null) FontWeight.Normal else FontWeight.SemiBold), color = if (v == null) Theme.muted else Theme.ink)
        FlexSpacer(0.dp)   // ios: Views/ShelterView.swift:916
    }
}

// ios: Views/ShelterView.swift:920
@Composable
private fun Section(t: String, body: String) {
    Card {   // ios: Views/ShelterView.swift:921
        T(t, style = appFont(15f, FontWeight.Bold))   // ios: Views/ShelterView.swift:922
        T(body, style = appFont(14f).ls(4f))   // ios: Views/ShelterView.swift:923
    }
}

// 캐릭터 받침: 원래 남색. 검은 후드(이르)·흑발(수아)만 조금 밝게 해서 머리가 보이게 한다
// ios: Views/ShelterView.swift:928,930
fun Shelter.spriteBacking(id: String): Color =
    if (id == "ir" || id == "sua") hex(0x3A4566) else hex(0x232B42)   // ios: Views/ShelterView.swift:931
