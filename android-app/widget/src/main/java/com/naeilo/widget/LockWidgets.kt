package com.naeilo.widget

import android.service.quicksettings.TileService
import com.naeilo.app.core.Store
import com.naeilo.app.core.WL
import com.naeilo.app.core.WidgetLine
import java.time.Instant

// 잠금화면 위젯 4개(LockAsset·LockGoal·LockFuture·LockTarget)는 안드로이드에 없어 옮기지 않는다 (L 브리프). 위젯 묶음 순서는 Widgets.all

/** 빠른 설정 ↻ (아이폰 제어 센터 ↻ 자리): 누르면 위젯의 ↻ 와 같이 지금 가격을 받고 모든 위젯을 다시 그린다 */
// ios: Widgets/LockWidgets.swift:35
class ReloadControl : TileService() {
    companion object {
        // ios: Widgets/LockWidgets.swift:37
        const val kind = "control.reload"
        /** 타일 기호 (매니페스트의 ↻ 그림) */
        // ios: Widgets/LockWidgets.swift:39
        const val systemImage = "arrow.clockwise"
    }
    // ios: Widgets/LockWidgets.swift:36
    override fun onStartListening() {
        super.onStartListening()
        // ios: Widgets/LockWidgets.swift:39,42,43
        qsTile?.apply { label = WL.t("naeilo 다시 받기"); if (android.os.Build.VERSION.SDK_INT >= 29) subtitle = WL.t("위젯의 지금 가격을 바로 받아 다시 그려요"); updateTile() }
    }
    // ios: Widgets/LockWidgets.swift:37,38
    override fun onClick() {
        super.onClick()
        Store.init(this)
        Store.reloadingSince = Instant.now()
        WidgetLine.bump(minGap = 0.0)
        Widgets.reloadAllTimelinesAsync(this)
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork("naeilo.reload", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<ReloadWork>().build())
    }
}
