package com.naeilo.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.naeilo.app.core.Engine
import com.naeilo.app.core.Fresh
import com.naeilo.app.core.Snapshot
import com.naeilo.app.core.sample
import com.naeilo.app.core.Store
import com.naeilo.app.core.WidgetLine
import com.naeilo.app.core.WidgetRefresh
import com.naeilo.app.core.epochSec
import com.naeilo.app.core.toInstant
import java.time.Instant
import java.util.concurrent.TimeUnit

// ios: Widgets/Provider.swift:5
class Entry(
    // ios: Widgets/Provider.swift:6
    val date: Instant,
    // ios: Widgets/Provider.swift:7
    val snap: Snapshot,
    // ios: Widgets/Provider.swift:8
    val preview: Boolean = false,     // 위젯 갤러리 미리보기면 잠그지 않고 실제 모습을 보여 준다 (안드로이드 갤러리는 그림만이라 늘 false)
)

/** 기본 위젯 값. 위젯이 다시 그려질 때마다 지금 값을 읽는다 (Engine 이 연결 전이면 예시 값을 준다) */
// ios: Widgets/Provider.swift:11
object Provider {
    // ios: Widgets/Provider.swift:12
    fun placeholder(): Entry = Entry(date = Instant.now(), snap = Snapshot.sample)

    // ios: Widgets/Provider.swift:14,16
    fun getSnapshot(): Entry = Entry(date = Instant.now(), snap = Engine.snapshot(), preview = false)
}

/** 정기 갱신 (아이폰 타임라인): 지금 가격·환율을 받고, 아래 줄 '몇 분 전'이 바뀔 즈음마다 다시 그린다.
 *  다음 받기 시각은 WidgetRefresh (기본·인물 위젯이 같이 쓴다). 작업 하나만 예약해 두고 끝나면 다음 것을 예약한다 */
// ios: Widgets/Provider.swift:19
object Refresh {
    private const val name = "naeilo.widget.refresh"

    /** 다음 깨울 때 예약: '몇 분 전' 다시 그리기와 다음 받기 중 이른 쪽 */
    fun plan(ctx: Context, next: Instant? = null) {
        Store.init(ctx)
        val d = Store.defaults
        next?.let { d.edit().putLong("widgetNext", it.toEpochMilli()).putLong("widgetDrawn", Instant.now().toEpochMilli()).apply() }
        val due = Instant.ofEpochMilli(d.getLong("widgetNext", 0L))
        val drawn = Instant.ofEpochMilli(d.getLong("widgetDrawn", System.currentTimeMillis()))
        val now = Instant.now()
        // 같은 값을 몇 장 더 그려 둔다: 아래 줄의 '몇 분 전'이 시간이 지나면서 바뀌게
        // ios: Widgets/Provider.swift:29,30
        val at = (Fresh.dates(drawn, until = due).filter { it > now } + due).minOrNull() ?: now
        val delay = maxOf(0L, at.toEpochMilli() - now.toEpochMilli())
        WorkManager.getInstance(ctx).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWork>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    // ios: Widgets/Provider.swift:20
    suspend fun getTimeline(ctx: Context) {
        Store.init(ctx)
        if (Widgets.all.none { androidx.glance.appwidget.GlanceAppWidgetManager(ctx).getGlanceIds(it.javaClass).isNotEmpty() }) return   // 둔 위젯이 없으면 받지 않는다
        // ↻ 직후 '받는 중' 그리기: 받지 않고 바로 그린다 (받기는 ReloadIntent 가 하고, 끝나면 다시 그린다)
        // ios: Widgets/Provider.swift:22,23,24
        if (Store.isReloading) { plan(ctx, Instant.now().plusSeconds(15)); return }
        val due = Store.defaults.getLong("widgetNext", 0L)
        if (System.currentTimeMillis() + 30_000 >= due) {
            // 지금 가격·환율 받기와 다음 갱신 시각은 인물 위젯과 같이 쓴다 (core/Format.kt 의 WidgetRefresh)
            // ios: Widgets/Provider.swift:27
            val next = WidgetRefresh.fetch()
            WidgetLine.bump()   // 인물 위젯: 새로 그릴 때마다 다음 대사
            Widgets.reloadAllTimelines(ctx)
            plan(ctx, next)
        } else {
            Widgets.reloadAllTimelines(ctx)
            plan(ctx)
        }
    }
}

class RefreshWork(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { Refresh.getTimeline(applicationContext); return Result.success() }
}

/** 7일 무료가 끝나는 때 (story 위젯 잠금용) */
fun trialEndInstant(): Instant? = Store.trialEnd.takeIf { !Store.subOn && it > Instant.now().epochSec }?.toInstant()
