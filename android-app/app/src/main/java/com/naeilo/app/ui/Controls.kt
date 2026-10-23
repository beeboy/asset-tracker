package com.naeilo.app.ui

import androidx.compose.ui.unit.isSpecified
import com.naeilo.app.core.tr
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.naeilo.app.R
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.math.max

// iOS 기본 부품(Toggle·Picker 분할·SecureField·confirmationDialog·sheet·Menu·NavigationStack·refreshable·Divider)의 안드로이드 자리.
// SwiftUI 에 원래 있는 부품이라 iOS 원본 줄이 없다 (port_check 대상 아님). 모양·크기는 iOS 17 기본값을 따른다.
// 새 라이브러리 없이 foundation 만으로 그린다.

/** iOS 시스템 회색들 (밝게·어둡게) */
private object Sys {
    fun fill(dark: Boolean) = if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA)          // Toggle 꺼짐 바탕 (systemFill)
    fun seg(dark: Boolean) = if (dark) Color(0xFF1C1C1F) else Color(0xFFEEEEEF)           // 분할 바탕 (tertiarySystemFill)
    fun segOn(dark: Boolean) = if (dark) Color(0xFF636366) else Color.White               // 분할 고른 칸
    fun placeholder(dark: Boolean) = if (dark) Color(0x4DEBEBF5) else Color(0x4D3C3C43)   // placeholderText
    fun sheetBg(dark: Boolean) = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    fun actionBg(dark: Boolean) = if (dark) Color(0xFF2C2C2E) else Color(0xEBF9F9F9)
    fun actionSep(dark: Boolean) = if (dark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)
    val gray = Color(0xFF8E8E93)
    val red = Color(0xFFFF3B30)
    val blue = Color(0xFF007AFF)
}

// ── 그림 ──

private val drawableIds = HashMap<String, Int>()

/** 이름으로 그림 찾기 (iOS Image("이름")). 한 번 찾은 값은 둔다. 없으면 0 */
@SuppressLint("DiscouragedApi")
fun drawableId(ctx: Context, name: String): Int =
    drawableIds.getOrPut(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }

/** 도트 그림을 이름으로: 보간 없이 키운다 (Pixel 과 같은 그림, modifier·흐림을 더 받는다) */
@Composable
fun PixelN(name: String, width: Dp, height: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val id = drawableId(LocalContext.current, name)
    if (id == 0) { Box(modifier.size(width, height)); return }
    Image(ImageBitmap.imageResource(id), null, modifier.size(width, height), filterQuality = FilterQuality.None,
        contentScale = ContentScale.FillBounds, alpha = alpha)
}

/** 매끈한 그림(앱 아이콘 미리보기 등): 보간해서 줄인다 */
@Composable
fun SmoothImage(name: String, size: Dp, modifier: Modifier = Modifier) {
    val id = drawableId(LocalContext.current, name)
    if (id == 0) { Box(modifier.size(size)); return }
    Image(ImageBitmap.imageResource(id), null, modifier.size(size), filterQuality = FilterQuality.High, contentScale = ContentScale.Fit)
}

/** SF Symbol 이름 → Material Symbols 벡터 (tools/port_check/symbols.md 와 같은 표) */
val SF: Map<String, Int> = mapOf(
    "chevron.right" to R.drawable.ms_chevron_right, "lock" to R.drawable.ms_lock, "lock.fill" to R.drawable.ms_lock_fill,
    "arrow.up.right" to R.drawable.ms_arrow_outward, "iphone" to R.drawable.ms_smartphone, "server.rack" to R.drawable.ms_dns,
    "chart.line.uptrend.xyaxis" to R.drawable.ms_show_chart, "info.circle.fill" to R.drawable.ms_info_fill, "key.fill" to R.drawable.ms_key_fill,
    "checkmark.circle.fill" to R.drawable.ms_check_circle_fill, "checkmark" to R.drawable.ms_check, "desktopcomputer" to R.drawable.ms_desktop_windows,
    "arrow.triangle.2.circlepath" to R.drawable.ms_sync, "square.and.arrow.up" to R.drawable.ms_ios_share, "stairs" to R.drawable.ms_stairs,
    "square.stack.3d.up" to R.drawable.ms_stacks, "tv" to R.drawable.ms_tv, "ruler" to R.drawable.ms_straighten, "puzzlepiece" to R.drawable.ms_extension,
    "camera.macro" to R.drawable.ms_local_florist, "arrow.left.and.right" to R.drawable.ms_arrow_range, "person.3.fill" to R.drawable.ms_groups_fill,
    "plus" to R.drawable.ms_add, "chevron.down" to R.drawable.ms_expand_more, "trash" to R.drawable.ms_delete, "pencil" to R.drawable.ms_edit,
    "flag" to R.drawable.ms_flag, "calendar" to R.drawable.ms_calendar_month, "building.columns" to R.drawable.ms_account_balance,
    "arrow.left.arrow.right" to R.drawable.ms_swap_horiz, "chart.pie" to R.drawable.ms_pie_chart, "arrow.down.to.line" to R.drawable.ms_download,
    "arrow.up.to.line" to R.drawable.ms_upload, "chevron.up" to R.drawable.ms_expand_less, "rectangle.3.group" to R.drawable.ms_rect3_group, "gearshape" to R.drawable.tab_settings,
    "minus" to R.drawable.ms_remove, "xmark" to R.drawable.ms_close, "info.circle" to R.drawable.ms_info,
    "bell" to R.drawable.ms_notifications, "bell.fill" to R.drawable.ms_notifications_fill,
    "clock" to R.drawable.ms_schedule, "sun.max.fill" to R.drawable.ms_sunny_fill, "circle" to R.drawable.ms_radio_button_unchecked,
    "sparkles" to R.drawable.ms_auto_awesome, "hourglass" to R.drawable.ms_hourglass_empty, "newspaper" to R.drawable.ms_newspaper, "gift" to R.drawable.ms_redeem,
    "star.fill" to R.drawable.ms_star_fill, "medal.fill" to R.drawable.ms_military_tech_fill, "trophy.fill" to R.drawable.ms_trophy_fill, "crown.fill" to R.drawable.ms_workspace_premium_fill,
    "square.grid.3x3.fill" to R.drawable.ms_grid_on, "water.waves" to R.drawable.ms_waves, "gamecontroller" to R.drawable.ms_sports_esports,
    "nosign" to R.drawable.ms_block, "arrow.counterclockwise" to R.drawable.ms_replay,
    "circle.dotted" to R.drawable.ms_radio_button_partial, "play.fill" to R.drawable.ms_play_arrow_fill, "photo" to R.drawable.ms_image,
    "questionmark.circle" to R.drawable.ms_help, "hand.tap" to R.drawable.ms_touch_app, "plus.square" to R.drawable.ms_add_box, "magnifyingglass" to R.drawable.ms_search, "square.grid.2x2" to R.drawable.ms_grid_view, "person.crop.square" to R.drawable.ms_account_box,
    "pause.fill" to R.drawable.ms_pause_fill, "trophy" to R.drawable.ms_trophy, "star" to R.drawable.ms_star, "heart" to R.drawable.ms_favorite,
    "heart.fill" to R.drawable.ms_favorite_fill, "cloud.fill" to R.drawable.ms_cloud_fill, "circle.fill" to R.drawable.ms_circle_fill,
)

/** Image(systemName:) 자리. size = 글자 크기(pt), 기호 칸은 글자 크기의 1.25배. scaled = 큰 글자 설정을 따를지 (.font(.system(size:)) 도 따른다) */
@Composable
fun Sym(sf: String, size: Float, color: Color, modifier: Modifier = Modifier, scaled: Boolean = true) {
    val k = if (scaled) LocalTextScale.current.ratio else 1f
    Image(painterResource(SF.getValue(sf)), null, modifier.size((size * 1.25f * k).dp), colorFilter = ColorFilter.tint(color))
}

// ── 선 ──

/** Divider(): 1 화소 선 */
@Composable
fun HDivider(color: Color = Theme.line, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { 1.toDp() }
    Box(modifier.fillMaxWidth().height(px).background(color))
}

// ── Toggle ──

/** iOS 스위치: 51×31, 손잡이 27, 켜면 tint */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, tint: Color = Theme.teal) {
    val dark = LocalPal.current.isDark
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.75f, stiffness = stiffness(0.35f)), label = "sw")
    val bg = if (checked) tint else Sys.fill(dark)
    Box(
        Modifier.size(51.dp, 31.dp).background(bg, CapsuleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** Toggle(isOn:) { 글 }: 글 왼쪽, 스위치 오른쪽. 줄 어디를 눌러도 바뀐다 */
@Composable
fun ToggleRow(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, tint: Color = Theme.teal, label: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .semantics(mergeDescendants = true) { role = Role.Switch; stateDescription = tr(if (checked) "켬" else "끔") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { label() }
        Box(Modifier.padding(start = 8.dp)) { IosSwitch(checked, onChange, tint) }
    }
}

// ── 분할 Picker ──

/** Picker(.segmented): 높이 32, 고른 칸은 흰 판 */
@Composable
fun <K> Segmented(items: List<Pair<K, String>>, selection: K, onSelect: (K) -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalPal.current.isDark
    Row(modifier.fillMaxWidth().height(32.dp).background(Sys.seg(dark), RoundedCornerShape(9.dp)).padding(2.dp)) {
        items.forEach { (k, t) ->
            val on = k == selection
            Box(
                Modifier.weight(1f).fillMaxSize()
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(7.dp)).background(Sys.segOn(dark), RoundedCornerShape(7.dp)) else Modifier)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(k) },
                contentAlignment = Alignment.Center,
            ) { T(t, style = TextStyle(fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = Theme.ink, maxLines = 1) }
        }
    }
}

// ── 글 넣는 칸 ──

/** TextField·SecureField 자리: 비어 있으면 회색 안내 글. secure 면 •로 가린다 */
@Composable
fun InputField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secure: Boolean = false,
    number: Boolean = false,
    style: TextStyle = appFont(17f),
    onSubmit: (() -> Unit)? = null,
) {
    val dark = LocalPal.current.isDark
    val ink = Theme.ink
    BasicTextField(
        value, onChange, modifier.fillMaxWidth(),
        textStyle = style.copy(color = ink),
        singleLine = true,
        cursorBrush = SolidColor(Theme.teal),
        visualTransformation = if (secure) PasswordVisualTransformation('•') else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = if (secure) KeyboardType.Password else if (number) KeyboardType.Number else KeyboardType.Text,
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }, onGo = { onSubmit?.invoke() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) T(placeholder, style = style, color = Sys.placeholder(dark), maxLines = 1)
                inner()
            }
        },
    )
}

// ── 고르기 창 (confirmationDialog) ──

class DialogAction(val title: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** confirmationDialog(titleVisibility: .visible): 아래에서 올라오는 고르기 판 + 따로 떨어진 '취소' */
@Composable
fun ConfirmSheet(show: Boolean, title: String, message: String?, actions: List<DialogAction>, cancel: String = "취소", onDismiss: () -> Unit) {
    if (!show) return
    val dark = LocalPal.current.isDark
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().background(Sys.actionBg(dark), RoundedCornerShape(14.dp))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Sys.gray)
                        if (message != null) T(message, style = TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Sys.gray)
                    }
                    actions.forEach { a ->
                        HDivider(Sys.actionSep(dark))
                        Box(Modifier.fillMaxWidth().height(57.dp).clickable { onDismiss(); a.onClick() }, contentAlignment = Alignment.Center) {
                            T(a.title, style = appFont(20f), color = if (a.destructive) Sys.red else Sys.blue)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(57.dp).background(if (dark) Color(0xFF2C2C2E) else Color.White, RoundedCornerShape(14.dp)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    T(cancel, style = appFont(20f, FontWeight.SemiBold), color = Sys.blue)
                }
            }
        }
    }
}

// ── 시트 ──

/** .sheet: 위를 조금 남기고 올라오는 판 (위 모서리 10). 뒤로 가기·바깥을 누르면 닫힌다. medium = 화면 절반 높이 */
@Composable
fun Sheet(show: Boolean, onDismiss: () -> Unit, medium: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (!show) return
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val y = remember { Animatable(1f) }
        LaunchedEffect(Unit) { y.animateTo(0f, spring(dampingRatio = 1f, stiffness = stiffness(0.5f))) }
        // 뒤로 쓸기·바깥 누르기: 판이 손가락을 따라 아래로 내려가고 다 내려가면 닫힌다 (ios: .sheet 끌어내리기)
        val swipe = rememberBackSwipe(true, onDismiss)
        // 키보드가 뜨면 판을 키보드 위로 올린다 (어두운 바탕은 그대로 전체, ime 를 먼저 써서 안쪽 safeDrawing 과 겹치지 않음)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - swipe.p.value }.background(Color(0x66000000)).clickable(remember { MutableInteractionSource() }, indication = null, onClick = swipe.pop).windowInsetsPadding(WindowInsets.ime), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth()
                    .then(if (medium) Modifier.fillMaxSize(0.5f) else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 10.dp).fillMaxSize())
                    .graphicsLayer { translationY = maxOf(y.value, swipe.p.value) * size.height }
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(Theme.bg)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) { content() }
        }
    }
}

/** 시트 맨 위 줄: 가운데 제목 + 오른쪽 버튼 (navigationTitle·inline + ToolbarItem(.topBarTrailing)) */
@Composable
fun SheetBar(title: String?, trailing: String? = null, onClick: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
        if (title != null) T(title, Modifier.align(Alignment.Center), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        if (trailing != null) T(trailing, Modifier.align(Alignment.CenterEnd).plainClickable(onClick), style = TextStyle(fontSize = 17.sp), color = Theme.teal)
    }
}

// ── 메뉴 ──

/** 메뉴 한 줄 (Menu 안의 Button) */
class MenuItem(val title: String, val icon: String? = null, val destructive: Boolean = false, val onClick: () -> Unit)

/** Menu { Button… } label: 누르면 이름표 옆에 목록이 뜬다 */
@Composable
fun DropMenu(items: List<MenuItem>, label: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val dark = LocalPal.current.isDark
    Box {
        Box(Modifier.plainClickable { open = true }) { label() }
        if (open) Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, 0), onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(250.dp).shadow(16.dp, RoundedCornerShape(13.dp)).background(Sys.actionBg(dark), RoundedCornerShape(13.dp))) {
                items.forEachIndexed { i, it0 -> val t = it0.title; val a = it0.onClick
                    if (i > 0) HDivider(Sys.actionSep(dark))
                    Row(Modifier.fillMaxWidth().height(44.dp).clickable { open = false; a() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(24.dp)) { it0.icon?.let { Sym(it, 15f, if (it0.destructive) Sys.red else Theme.ink, scaled = false) } }
                        T(t, style = TextStyle(fontSize = 17.sp), color = if (it0.destructive) Sys.red else Theme.ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ── 화면 쌓기 (NavigationStack) ──

/** iOS 이동 막대 (inline): 왼쪽 '‹ 뒤로', 가운데 제목. 높이 44 */
@Composable
fun NavBar(title: String?, back: String? = null, center: (@Composable () -> Unit)? = null, onBack: () -> Unit) {
    // 이동 막대 글자도 큰 글자 설정을 조금 따른다 (iOS 처럼 커지되 막대 높이는 그대로)
    val k = minOf(LocalTextScale.current.ratio, 1.35f)
    val tint = Theme.teal   // 탭 tint
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Row(
            Modifier.align(Alignment.CenterStart).plainClickable(onBack).padding(start = 8.dp, end = 12.dp).height(44.dp)
                .semantics { role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(12.dp * k, 21.dp * k)) {
                val w = 2.6.dp.toPx()
                drawLine(tint, Offset(size.width - w, w), Offset(w, size.height / 2), w, StrokeCap.Round)
                drawLine(tint, Offset(w, size.height / 2), Offset(size.width - w, size.height - w), w, StrokeCap.Round)
            }
            T(back?.takeIf { it.length <= 12 } ?: "뒤로", Modifier.padding(start = 6.dp), style = TextStyle(fontSize = (17 * k).sp), color = tint, maxLines = 1)
        }
        if (center != null) Box(Modifier.align(Alignment.Center)) { center() }
        else if (title != null) T(title, Modifier.align(Alignment.Center), style = TextStyle(fontSize = (17 * k).sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}


/** 쌓인 화면 하나의 이동 막대 제목 (navigationTitle). null 이면 제목 없음 */
class NavPage(val title: String? = null, val bar: Boolean = true, val content: @Composable () -> Unit)

/** 쌓인 화면이 이동 막대를 스스로 그릴 때(bar = false) 쓰는 뒤로 가기·뒤로 글자 */
class NavBack(val back: String?, val pop: () -> Unit)
val LocalNavBack = androidx.compose.runtime.staticCompositionLocalOf<NavBack?> { null }

/**
 * 뒤로 쓸기 (예측 뒤로 가기). p = 0 제자리 … 1 다 빠짐. 쓰는 동안 손가락을 따라가고, 놓으면 끝까지 간 뒤 onBack, 되돌리면 제자리.
 * pop = 버튼으로 뒤로 갈 때도 같은 움직임으로 빠진다. ios: NavigationStack 가장자리 스와이프 백 · .sheet 끌어내리기
 */
class BackSwipe(val p: Animatable<Float, *>, val pop: () -> Unit)

@Composable
fun rememberBackSwipe(enabled: Boolean, onBack: () -> Unit): BackSwipe {
    val reduce by rememberUpdatedState(LocalReduceMotion.current)
    val back by rememberUpdatedState(onBack)
    val p = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val busy = remember { booleanArrayOf(false) }   // 빠지는 중에 한 번 더 눌러도 두 칸 뒤로 가지 않게
    suspend fun finish() {
        if (busy[0]) return
        busy[0] = true
        try { if (!reduce) p.animateTo(1f, tween(220)); back(); p.snapTo(0f) } finally { busy[0] = false }
    }
    PredictiveBackHandler(enabled) { events ->
        try { events.collect { if (!busy[0]) p.snapTo(it.progress) }; finish() }
        catch (e: CancellationException) { scope.launch { p.animateTo(0f, tween(200)) }; throw e }
    }
    return remember { BackSwipe(p) { scope.launch { finish() } } }
}

/**
 * NavigationStack(path:) 자리. 첫 화면은 늘 그려 둔 채(스크롤 자리 유지) 위에 맨 위 화면만 덮는다.
 * 새 화면은 오른쪽에서 밀려 들어온다 (0.35초). 뒤로 가기(제스처·버튼)는 맨 위 하나를 뺀다:
 * 아이폰처럼 맨 위 화면이 오른쪽으로 밀려나고 아래 화면이 왼쪽에서 조금 따라 들어오며 드러난다.
 */
@Composable
fun <T> NavStack(path: List<T>, onPath: (List<T>) -> Unit, root: @Composable () -> Unit, page: @Composable (T) -> NavPage) {
    val reduce = LocalReduceMotion.current
    val swipe = rememberBackSwipe(path.isNotEmpty()) { onPath(path.dropLast(1)) }
    val sliding by remember { derivedStateOf { swipe.p.value > 0f } }   // 쓰는 동안만 아래 화면을 그린다
    var seen by remember { mutableIntStateOf(path.size) }   // 직전 쌓임 수: 늘었을 때만 새 화면이 밀려 들어온다 (뒤로 가서 드러난 화면은 그대로)
    val pushed = path.size > seen
    SideEffect { seen = path.size }
    val under = -0.3f   // 아래 화면이 숨어 있는 자리 (화면 폭 비율, ios 와 같은 시차)
    Layout(content = {
        Box(Modifier.fillMaxSize().graphicsLayer { if (path.size == 1) translationX = under * (1f - swipe.p.value) * size.width }) { root() }
        val from = if (sliding && path.size >= 2) path.size - 2 else path.size - 1
        for (i in maxOf(from, 0) until path.size) {
            val item = path[i]
            val isTop = i == path.size - 1
            key(i, item) {
                val x = remember { Animatable(if (!reduce && isTop && pushed) 1f else 0f) }
                LaunchedEffect(Unit) { if (x.value > 0f) x.animateTo(0f, tween(350, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))) }
                val p = page(item)
                Column(Modifier.fillMaxSize().graphicsLayer {
                    translationX = if (isTop) maxOf(x.value, swipe.p.value) * size.width else under * (1f - swipe.p.value) * size.width
                }.background(Theme.bg)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                    val back = if (i >= 1) page(path[i - 1]).title else null   // 뒤로 글자 = 앞 화면 제목 (없으면 뒤로)
                    if (p.bar) NavBar(p.title, back = back, onBack = swipe.pop)
                    Box(Modifier.weight(1f)) { androidx.compose.runtime.CompositionLocalProvider(LocalNavBack provides NavBack(back, swipe.pop)) { p.content() } }
                }
            }
        }
    }) { ms, c ->
        val ps = ms.map { it.measure(c) }
        layout(c.maxWidth, c.maxHeight) {
            // 덮인 첫 화면은 들어오는 동안만 보인다 (다 덮으면 그리지 않아도 되지만 자리는 남겨 스크롤을 지킨다)
            ps.forEach { it.place(0, 0) }
        }
    }
}

// ── 끌면 자판 내리기 (.scrollDismissesKeyboard) ──

/** 손가락으로 위아래로 끌 때만 자판을 내린다. 자판이 뜰 때 입력칸을 보이게 올리는 자동 스크롤에는 반응하지 않는다 (숫자칸이 열리자마자 닫히던 것) */
fun Modifier.hideKeyboardOnDrag(focus: androidx.compose.ui.focus.FocusManager): Modifier = this.pointerInput(focus) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        while (true) {
            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            if (kotlin.math.abs(c.position.y - down.position.y) > slop) { focus.clearFocus(); break }
        }
    }
}

// ── 당겨서 새로 고침 (.refreshable) ──

/** 맨 위에서 더 당기면(70) 돌아가는 표시를 보이고 onRefresh 를 한 번 부른다. 끝나면 제자리로 */
@Composable
fun Refreshable(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    val limit = with(density) { 70.dp.toPx() }
    val pull = remember { Animatable(0f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 당긴 채로 위로 밀면 당긴 만큼 먼저 줄인다
                if (available.y < 0 && pull.value > 0 && !busy) {
                    val use = maxOf(available.y, -pull.value)
                    scope.launch { pull.snapTo(pull.value + use) }
                    return Offset(0f, use)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput && !busy) {
                    scope.launch { pull.snapTo(pull.value + available.y * 0.5f) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull.value >= limit && !busy) {
                    busy = true
                    pull.animateTo(limit)
                    try { onRefresh() } finally { busy = false; pull.animateTo(0f) }
                } else if (pull.value > 0) pull.animateTo(0f)
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    Box(modifier.nestedScroll(conn)) {
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = pull.value }) { content() }
        if (pull.value > 1f) {
            val spin = remember { Animatable(0f) }
            LaunchedEffect(busy) { if (busy) while (true) { spin.snapTo(0f); spin.animateTo(1f, tween(800, easing = LinearEasing)) } }
            val a = (pull.value / limit).coerceIn(0f, 1f)
            val gray = Sys.gray
            Canvas(Modifier.align(Alignment.TopCenter).offset { IntOffset(0, (pull.value / 2 - with(density) { 12.dp.toPx() }).roundToInt()) }.size(24.dp)) {
                val r = size.minDimension / 2; val c = center
                for (i in 0 until 8) {
                    if (!busy && i >= (a * 8).toInt()) continue
                    val ang = Math.toRadians((i * 45 - 90 + spin.value * 360).toDouble())
                    val al = if (busy) 0.25f + 0.75f * (((i - spin.value * 8) % 8 + 8) % 8) / 8f else 0.8f
                    drawLine(gray.copy(alpha = al), Offset(c.x + (r * 0.45f) * Math.cos(ang).toFloat(), c.y + (r * 0.45f) * Math.sin(ang).toFloat()),
                        Offset(c.x + r * Math.cos(ang).toFloat(), c.y + r * Math.sin(ang).toFloat()), 2.5.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}


// ── 글자·색 도움 ──

/** Color(hex:dark:): 화면 모드에 따라 */
@Composable
fun hexD(light: Long, dark: Long): Color = if (LocalPal.current.isDark) hex(dark) else hex(light)

/** .lineSpacing(n): 줄 사이에 n pt 를 더한다 (SF 기본 줄 높이 ≈ 글자 × 1.2) */
fun TextStyle.ls(extra: Float): TextStyle = copy(lineHeight = (fontSize.value * 1.2f + extra).sp)

/** .foregroundStyle(색) 을 안쪽 글자 기본색으로 */
@Composable
fun Fg(color: Color, content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalFg provides { color }, content = content)

/** 꾸민 글 한 줄 (굵게 등) */
@Composable
fun TA(text: androidx.compose.ui.text.AnnotatedString, modifier: Modifier = Modifier, style: TextStyle = appFont(17f), color: Color = LocalFg.current(LocalPal.current), maxLines: Int = Int.MAX_VALUE) {
    androidx.compose.foundation.text.BasicText(com.naeilo.app.core.tr(text), modifier, style.copy(color = color), maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

/** .lineLimit(1).minimumScaleFactor(k): 한 줄로, 자리가 모자라면 글자를 k 배까지 줄인다 */
@Composable
fun FitText(text: String, style: TextStyle, minScale: Float, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") maxLines: Int = 1, color: Color = LocalFg.current(LocalPal.current), roll: Any? = null) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val text = com.naeilo.app.core.tr(text)   // 잴 때도 영어로
    // 폭을 재서 글자 크기를 정하고, 첫 줄 기준선(FirstBaseline)을 밖으로 넘겨 옆 글자와 기준선을 맞출 수 있게 한다
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val w = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val k = if (c.hasBoundedWidth && w > c.maxWidth) maxOf(minScale, c.maxWidth.toFloat() / w) else 1f
        val st = if (k < 1f) style.copy(fontSize = style.fontSize * k, letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing * k else style.letterSpacing) else style   // 정해지지 않은 자간은 곱하면 죽는다
        val p = subcompose(k) { T(text, style = st, color = color, maxLines = 1, roll = roll) }.first().measure(c.copy(minWidth = 0))
        val b = p[androidx.compose.ui.layout.FirstBaseline]
        // 칸이 더 넓으면(가득 채우기) 글은 앞(왼쪽)에 둔다. 제 폭만 알리면 바깥이 가운데에 놓는다
        layout(maxOf(p.width, c.minWidth), maxOf(p.height, c.minHeight), if (b != androidx.compose.ui.layout.AlignmentLine.Unspecified) mapOf(androidx.compose.ui.layout.FirstBaseline to b) else emptyMap()) { p.place(0, 0) }
    }
}

/** .padding(.horizontal, -n): 양옆으로 n 만큼 넓힌다 */
fun Modifier.outset(h: Dp): Modifier = this.layout { m, c ->
    val px = h.roundToPx()
    val p = m.measure(c.copy(minWidth = 0, maxWidth = if (c.hasBoundedWidth) c.maxWidth + px * 2 else c.maxWidth))
    layout(c.maxWidth, p.height) { p.place(-px, 0) }
}

/** @AppStorage("키") Bool 자리: 앱 설정(naeilo) 에 남는다 */
@Composable
fun rememberPrefBool(key: String, default: Boolean): androidx.compose.runtime.MutableState<Boolean> {
    val prefs = com.naeilo.app.data.Defaults.prefs
    val s = remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    return remember(key) {
        object : androidx.compose.runtime.MutableState<Boolean> by s {
            override var value: Boolean
                get() = s.value
                set(v) { s.value = v; prefs.edit().putBoolean(key, v).apply() }
        }
    }
}


// ── 알림 창 (.alert) ──

/** .alert(제목, message:) { 버튼 }: 가운데 뜨는 작은 창 */
@Composable
fun IosAlert(show: Boolean, title: String, message: String?, buttons: List<DialogAction>, onDismiss: () -> Unit) {
    if (!show) return
    val dark = LocalPal.current.isDark
    Dialog(onDismiss) {
        Column(Modifier.width(270.dp).background(Sys.actionBg(dark), RoundedCornerShape(14.dp))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(title, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Theme.ink)
                if (message != null) T(message, style = TextStyle(fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center), color = Theme.ink)
            }
            buttons.forEach { b ->
                HDivider(Sys.actionSep(dark))
                Box(Modifier.fillMaxWidth().height(44.dp).clickable { onDismiss(); b.onClick() }, contentAlignment = Alignment.Center) {
                    T(b.title, style = TextStyle(fontSize = 17.sp), color = if (b.destructive) Sys.red else Sys.blue)
                }
            }
        }
    }
}

// ── 길게 누르기 ──

/** 누르면 onClick, 길게 누르면 onLongClick (물결 없이, 누르는 동안 흐리게) */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.plainCombinedClickable(onLongClick: () -> Unit, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this.graphicsLayer { alpha = if (pressed) 0.6f else 1f }.combinedClickable(src, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/** @AppStorage("키") String 자리 */
@Composable
fun rememberPrefString(key: String, default: String): androidx.compose.runtime.MutableState<String> {
    val prefs = com.naeilo.app.data.Defaults.prefs
    val s = remember(key) { mutableStateOf(prefs.getString(key, null) ?: default) }
    return remember(key) {
        object : androidx.compose.runtime.MutableState<String> by s {
            override var value: String
                get() = s.value
                set(v) { s.value = v; prefs.edit().putString(key, v).apply() }
        }
    }
}

/** HStack { … }.lineLimit(1).minimumScaleFactor(k): 한 줄 가로 묶음. 자리가 모자라면 묶음째 k 배까지 줄인다 */
@Composable
fun FitRow(minScale: Float, modifier: Modifier = Modifier, spacing: Dp = 0.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val p = subcompose(Unit) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) { content() }
        }.first().measure(androidx.compose.ui.unit.Constraints())
        val k = if (c.hasBoundedWidth && p.width > c.maxWidth) maxOf(minScale, c.maxWidth.toFloat() / p.width) else 1f
        val w = minOf((p.width * k).toInt(), if (c.hasBoundedWidth) c.maxWidth else Int.MAX_VALUE)
        layout(w, (p.height * k).toInt()) {
            p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
        }
    }
}

/** ViewThatFits(in: .horizontal): 가로로 다 들어가면 first, 아니면 second */
@Composable
fun FitsOr(modifier: Modifier = Modifier, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    androidx.compose.ui.layout.SubcomposeLayout(modifier) { c ->
        val a = subcompose("a", first).map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val w = a.maxOfOrNull { it.width } ?: 0
        if (!c.hasBoundedWidth || w <= c.maxWidth) {
            val h = a.maxOfOrNull { it.height } ?: 0
            layout(w, h) { a.forEach { it.place(0, 0) } }
        } else {
            val b = subcompose("b", second).map { it.measure(c.copy(minWidth = 0)) }
            layout(b.maxOfOrNull { it.width } ?: 0, b.maxOfOrNull { it.height } ?: 0) { b.forEach { it.place(0, 0) } }
        }
    }
}

/** .contextMenu: 길게 누르면 그 자리에 뜨는 메뉴 */
@Composable
fun ContextMenu(show: Boolean, items: List<MenuItem>, onDismiss: () -> Unit) {
    if (!show) return
    val dark = LocalPal.current.isDark
    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(Modifier.width(250.dp).shadow(16.dp, RoundedCornerShape(13.dp)).background(Sys.actionBg(dark), RoundedCornerShape(13.dp))) {
            items.forEachIndexed { i, it0 ->
                if (i > 0) HDivider(Sys.actionSep(dark))
                Row(Modifier.fillMaxWidth().height(44.dp).clickable { onDismiss(); it0.onClick() }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(it0.title, Modifier.weight(1f), style = TextStyle(fontSize = 17.sp), color = if (it0.destructive) Sys.red else Theme.ink, maxLines = 1)
                    it0.icon?.let { Sym(it, 17f, if (it0.destructive) Sys.red else Theme.ink, scaled = false) }
                }
            }
        }
    }
}

/** ProgressView(): 작게 도는 표시 (20) */
@Composable
fun Spinner(size: Dp = 20.dp) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 8f, androidx.compose.animation.core.infiniteRepeatable(tween(800, easing = LinearEasing)), label = "a")
    val gray = Sys.gray
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2; val c = center; val step = a.toInt()
        for (i in 0 until 8) {
            val ang = Math.toRadians((i * 45 - 90).toDouble())
            val al = 0.25f + 0.75f * (((i - step) % 8 + 8) % 8) / 8f
            drawLine(gray.copy(alpha = al), Offset(c.x + r * 0.45f * Math.cos(ang).toFloat(), c.y + r * 0.45f * Math.sin(ang).toFloat()),
                Offset(c.x + r * Math.cos(ang).toFloat(), c.y + r * Math.sin(ang).toFloat()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Slider(value:in:step:): 막대 4, 손잡이 28 (흰 동그라미). 끄는 동안 step 단위로 바꾼다 */
@Composable
fun IosSlider(value: Double, onChange: (Double) -> Unit, range: ClosedFloatingPointRange<Double>, step: Double, modifier: Modifier = Modifier, tint: Color = Theme.teal) {
    val dark = LocalPal.current.isDark
    val track = if (dark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(36.dp)) {
        val w = constraints.maxWidth.toFloat(); val thumb = with(LocalDensity.current) { 28.dp.toPx() }
        val f = ((value - range.start) / (range.endInclusive - range.start)).toFloat().coerceIn(0f, 1f)
        fun pick(x: Float) {
            val t = ((x - thumb / 2) / max(1f, w - thumb)).coerceIn(0f, 1f)
            val v = range.start + (Math.round(t * (range.endInclusive - range.start) / step) * step)
            if (v != value) onChange(v.coerceIn(range.start, range.endInclusive))
        }
        Box(Modifier.fillMaxSize().pointerInput(w) {
            awaitEachGesture {
                val d = awaitFirstDown(); pick(d.position.x); d.consume()
                while (true) { val e = awaitPointerEvent(); val c = e.changes.firstOrNull() ?: break; if (!c.pressed) break; pick(c.position.x); c.consume() }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2; val x = thumb / 2 + f * (size.width - thumb)
                drawLine(track, Offset(thumb / 2, y), Offset(size.width - thumb / 2, y), 4.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(thumb / 2, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
            }
            Box(Modifier.offset { IntOffset((f * (w - thumb)).roundToInt(), 0) }.align(Alignment.CenterStart).size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}
