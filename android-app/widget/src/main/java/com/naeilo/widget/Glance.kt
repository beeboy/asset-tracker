package com.naeilo.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.naeilo.app.core.Store
import kotlinx.coroutines.launch

// 안드로이드 홈 위젯 틀 (Glance): 위젯 한 장을 비트맵 한 장으로 그려 놓고, 누르는 자리(↻·금액)에 투명 버튼을 얹는다.
// 아이폰 WidgetKit 의 타임라인 대신, 다시 그릴 때마다 지금 값으로 새로 그린다 (Provider.kt 가 다시 그릴 때를 정한다)

/** 위젯 한 장: 누르면 갈 주소, 말읽기 문장, 그리기 */
class Face(val url: String, val speech: String, val draw: Pen.() -> Unit)

/** 다시 그리라는 표시 (Glance 상태 값을 바꾸면 위젯이 다시 그려진다) */
private val tick = intPreferencesKey("tick")
/** 인물 위젯이 고른 인물 (WhoEnum raw), 전광판 포즈 차례 */
val whoKey = androidx.datastore.preferences.core.stringPreferencesKey("who")

/** 칸 안쪽 여백 (dp) */
private const val padX = 7.5f
private const val padY = 14.5f

abstract class FaceWidget(val kind: String) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    /** 지금 값으로 위젯 한 장 (그리는 중이 아니라 그리기 전에 읽는다) */
    abstract fun face(ctx: Context, state: Preferences): Face

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Refresh.ensure(context)
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val ctx = LocalContext.current
        val state = currentState<Preferences>()
        val size = LocalSize.current
        val f = face(ctx, state)
        // 삼성 기본 위젯처럼 칸 안쪽으로 들여 그린다 (칸을 꽉 채우면 더 크고 길어 보인다)
        val r = render(ctx, size.width.value - padX * 2, size.height.value - padY * 2, isDark(ctx), designW = guideW(ctx), draw = f.draw)
        val open = actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f.url)).setPackage(ctx.packageName))
        Box(GlanceModifier.fillMaxSize().padding(horizontal = padX.dp, vertical = padY.dp).clickable(open)) {
            Image(ImageProvider(r.bitmap), contentDescription = f.speech, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            for (h in r.hits.take(8)) {   // Glance 한 칸에 10개까지
                val act = when (val a = h.act) {
                    Act.Reload -> actionRunCallback<ReloadIntent>()
                    Act.StoryReload -> actionRunCallback<StoryReloadIntent>()
                    is Act.Toggle -> actionRunCallback<ToggleAmountIntent>(actionParametersOf(ToggleAmountIntent.kind to a.kind))
                }
                Box(GlanceModifier.fillMaxSize().padding(start = h.r.left.coerceAtLeast(0f).dp, top = h.r.top.coerceAtLeast(0f).dp)) {
                    Box(GlanceModifier.size(h.r.width.dp, h.r.height.dp).clickable(act)) {}
                }
            }
        }
    }
}

/** 위젯 다시 그리기 (아이폰 WidgetCenter) */
object Widgets {
    /** 모든 naeilo 위젯을 지금 값으로 다시 그린다 */
    suspend fun reloadAllTimelines(ctx: Context) {
        for (w in all) bump(ctx, w)
    }
    /** 한 종류만 */
    suspend fun reloadTimelines(ctx: Context, ofKind: String) {
        all.filter { it.kind == ofKind }.forEach { bump(ctx, it) }
    }
    /** 전광판 인물 포즈 차례 (↻ 받는 동안 1초마다) */
    suspend fun charFrame(ctx: Context, i: Int) {
        Store.defaults.edit().putInt("charFrame", i).apply()
        reloadTimelines(ctx, "char.board")
    }
    private suspend fun bump(ctx: Context, w: FaceWidget) {
        // 받는 쪽(Receiver) 칸 번호로 직접 찾는다: getGlanceIds(종류)가 다른 종류 칸을 돌려주면 그 칸이 다른 위젯(자산 언덕 등)으로 바뀌어 그려질 수 있다
        val m = GlanceAppWidgetManager(ctx)
        val rc = receiverOf[w.javaClass] ?: return
        val ids = android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetIds(android.content.ComponentName(ctx, rc))
        for (id in ids.map { m.getGlanceIdBy(it) }) {
            updateAppWidgetState(ctx, id) { it[tick] = (it[tick] ?: 0) + 1 }
            w.update(ctx, id)
        }
    }
    /** 앱 쪽에서 부를 때 (앱은 코루틴 안이 아닐 수 있다) */
    fun reloadAllTimelinesAsync(ctx: Context) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { reloadAllTimelines(ctx.applicationContext); Refresh.plan(ctx.applicationContext) }
    }

    /** 위젯 추가 화면 순서 (아이폰 NaeiloWidgets 묶음 순서). 잠금화면 묶음(LockWidgets)은 안드로이드에 없다 */
    // ios: Widgets/LockWidgets.swift:9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28
    val naeiloWidgets: List<FaceWidget> get() = all
    /** 위젯 종류 → 받는 쪽 (칸 번호를 찾을 때·미리보기를 올릴 때) */
    val receiverOf: Map<Class<*>, Class<*>> by lazy { mapOf(
        CharHill::class.java to CharHillReceiver::class.java, CharLadder::class.java to CharLadderReceiver::class.java,
        CharTower::class.java to CharTowerReceiver::class.java, CharBoard::class.java to CharBoardReceiver::class.java,
        CharHeight::class.java to CharHeightReceiver::class.java, CharPuzzle::class.java to CharPuzzleReceiver::class.java,
        CharGarden::class.java to CharGardenReceiver::class.java, CharTug::class.java to CharTugReceiver::class.java,
        AssetSmall::class.java to AssetSmallReceiver::class.java, PathSmall::class.java to PathSmallReceiver::class.java,
        FutureSmall::class.java to FutureSmallReceiver::class.java, BlockSmall::class.java to BlockSmallReceiver::class.java,
        PaceSmall::class.java to PaceSmallReceiver::class.java, TargetSmall::class.java to TargetSmallReceiver::class.java,
        MovesSmall::class.java to MovesSmallReceiver::class.java, StoryWidget::class.java to StoryWidgetReceiver::class.java,
    ) }
    val all: List<FaceWidget> by lazy { listOf(
        CharHill(), CharLadder(), CharTower(), CharBoard(), CharHeight(), CharPuzzle(), CharGarden(), CharTug(),
        AssetSmall(), PathSmall(), FutureSmall(), BlockSmall(), PaceSmall(), TargetSmall(), MovesSmall(), StoryWidget(),
    ) }
}


/** 앱이 부르는 위젯 다시 그리기 (아이폰 WidgetCenter.shared). 뒤에서 그린다 */
object WidgetCenter {
    fun reloadAllTimelines() = Widgets.reloadAllTimelinesAsync(Store.ctx)
    fun reloadTimelines(ofKind: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { Widgets.reloadTimelines(Store.ctx, ofKind) }
    }
}
