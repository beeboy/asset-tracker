package com.naeilo.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.naeilo.app.core.AlpacaFeed
import com.naeilo.app.core.KrQuoteFeed
import com.naeilo.app.core.LongbridgeFeed
import com.naeilo.app.core.QuoteFeed
import com.naeilo.app.core.Store
import com.naeilo.app.core.TossFeed
import com.naeilo.app.core.WFeed
import com.naeilo.app.core.WidgetLine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** 위젯의 ↻ 버튼: 사이트 시세와 (내 키가 있으면) 토스 지금 가격을 바로 받고 모든 위젯을 다시 그린다 */
// ios: Widgets/Intent.swift:5,6
class ReloadIntent : ActionCallback {
    /** 이름 (아이폰 AppIntent title) */
    companion object { const val title = "다시 받기" }
    // ios: Widgets/Intent.swift:7
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        // 누르자마자 버튼을 '받는 중'으로 다시 그린다 (받기가 끝날 때까지 기다리면 반응이 없어 보인다)
        // ios: Widgets/Intent.swift:9
        Store.reloadingSince = Instant.now()
        // ios: Widgets/Intent.swift:10
        WidgetLine.bump(minGap = 0.0)   // ↻: 인물 대사도 다음 줄로 (바로 이어지는 다시 그리기는 세지 않는다)
        // ios: Widgets/Intent.swift:11
        Widgets.reloadAllTimelines(context)
        // 받기는 위젯 누름 처리 시간(10초) 밖에서 끝나도 되게 작업으로 넘긴다 ("다시 받기")
        WorkManager.getInstance(context).enqueueUniqueWork("naeilo.reload", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReloadWork>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build())
    }
}

/** ↻ 받기 (ReloadIntent 가 넘긴다) */
class ReloadWork(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        Store.init(context)
        // ios: Widgets/Intent.swift:12
        val before = Store.newestGot
        coroutineScope {
            // 받는 동안 전광판 인물이 1초마다 포즈를 돌린다 (아이폰 CharProvider 의 15장)
            // ios: Widgets/CharWidgets.swift:59,60,61
            val spin = launch { for (i in 0 until 15) { if (i == 0) continue; delay(1000); if (!Store.isReloading) break; Widgets.charFrame(context, i) } }
            // ios: Widgets/Intent.swift:13
            withTimeout(8.0) {
                // ios: Widgets/Intent.swift:14
                val site = async { QuoteFeed.fetch() }
                // ios: Widgets/Intent.swift:15
                val toss = async { TossFeed.widgetFetch(minAge = 0.0) }
                // ios: Widgets/Intent.swift:16
                val alpaca = async { AlpacaFeed.widgetFetch(minAge = 0.0) }
                // ios: Widgets/Intent.swift:17
                val lb = async { LongbridgeFeed.widgetFetch(minAge = 0.0) }
                // 국내 테스트 시세 종목도 ↻ 때 바로 (전에는 정기 갱신 때만 받아 ↻로는 안 바뀌었다)
                // ios: Widgets/Intent.swift:19
                val kr = async { KrQuoteFeed.fetch(Store.read(WFeed.serializer(), "feed.json")?.ky ?: emptyMap(), minAge = 0.0) }
                // ios: Widgets/Intent.swift:20
                listOf(site, toss, kr, alpaca, lb).forEach { runCatching { it.await() } }
            }
            spin.cancel()
        }
        // 하나도 새로 받지 못했으면 위젯 아래 줄에 '받기 실패'
        // ios: Widgets/Intent.swift:23
        Store.fetchFailed = Store.newestGot <= before
        // ios: Widgets/Intent.swift:24
        Store.reloadingSince = null
        Widgets.charFrame(context, 0)
        // ios: Widgets/Intent.swift:25,26
        Widgets.reloadAllTimelines(context)
        return Result.success()
    }
}

/** 서재 위젯의 ↻: 시세는 받지 않고 읽던 자리(story.json)로 서재 위젯만 다시 그린다.
 *  잠깐 '받는 중'으로 그렸다가 되돌려, 다른 위젯과 같은 흐림·뛰어오름 효과를 낸다 */
// ios: Widgets/Intent.swift:32,33
class StoryReloadIntent : ActionCallback {
    /** 이름 (아이폰 AppIntent title) */
    companion object { const val title = "서재 다시 그리기" }
    // ios: Widgets/Intent.swift:34
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        // ios: Widgets/Intent.swift:35
        Store.reloadingSince = Instant.now()
        // ios: Widgets/Intent.swift:36
        Widgets.reloadTimelines(context, ofKind = "story")
        // ios: Widgets/Intent.swift:37
        delay((800_000_000L / 1_000_000L))
        // ios: Widgets/Intent.swift:38
        Store.reloadingSince = null
        // ios: Widgets/Intent.swift:39,40
        Widgets.reloadTimelines(context, ofKind = "story")
    }
}

/** sec 초 안에 끝나지 않으면 그만둔다 (null) */
// ios: Widgets/Intent.swift:44,45,46,47,48,49,50
suspend fun <T> withTimeout(sec: Double, op: suspend () -> T): T? = withTimeoutOrNull((sec * 1e9 / 1e6).toLong()) { op() }

/** 큰 금액을 누르면 숨기기·다시 보이기 (기본은 모든 위젯이 같이) */
// ios: Widgets/Intent.swift:55
class ToggleAmountIntent : ActionCallback {
    companion object {
        /** 이름 (아이폰 AppIntent title) */
        // ios: Widgets/Intent.swift:56
        const val title = "금액 숨기기"
        // ios: Widgets/Intent.swift:57
        val kind = ActionParameters.Key<String>("위젯")
    }
    // ios: Widgets/Intent.swift:60
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        // ios: Widgets/Intent.swift:61
        Store.toggleHidden(parameters[kind] ?: return)
        // ios: Widgets/Intent.swift:62,63
        Widgets.reloadAllTimelines(context)
    }
}
