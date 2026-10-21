package com.naeilo.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.naeilo.app.AppModel
import com.naeilo.app.MainActivity
import com.naeilo.app.NOTIFY_OPEN
import com.naeilo.app.R
import com.naeilo.app.core.Day
import com.naeilo.app.core.NJson
import com.naeilo.app.core.calc.focusName
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.keyName
import com.naeilo.app.core.calc.keyValue
import com.naeilo.app.core.calc.trackValue
import com.naeilo.app.core.cformat
import com.naeilo.app.ui.drawableId
import com.naeilo.app.ui.settings.AppIconPicker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// 실제 알림 (기기 안에서 예약하는 로컬 알림. 서버 없음)
// - 본전 도달 · 비중 이탈: 앱이 시세를 갱신할 때(앱이 켜져 있을 때 1분마다) 조건을 보고 바로 보낸다.
//   둘이 같은 날 겹치면 하나로 묶고, 조건 알림은 하루 한 번까지. 본전 도달은 본전 아래로 내려갔다 다시 넘을 때만 또 보낸다.
// - 아침 한 줄: 다음 평일 아침 한 번만 지금 숫자로 예약하고, 그 뒤 평일 4번은 숫자 없이 예약한다.
//   앱을 열 때마다 다시 예약하므로, 앱을 자주 열면 늘 숫자가 들어간 알림이 온다. 숫자가 오래되면 넣지 않는다.
// - 연말 세금 정리: 매년 12월 1일 오전 9시.
// - 금리 결정 전날: 앞으로 90일 안의 FOMC·한국은행 결정 하루 전 오전 9시 (금리 충격 테스트에서 켬). 날짜는 고정 규칙이라 근사일 수 있다.
// - 일정 다음 날 결과: 외부 요인 '오늘의 이벤트' 카드에서 켠 일정만, 일정 다음 날 오전 9시 한 번 (id "xf.…").
//   금리 결정 '전날' 알림(id "rate.…")과는 시점·이름이 달라 겹치지 않는다.
//
// 안드로이드 예약 방법 (2026-10-21):
// - 정해진 시각 알림(아침 한 줄·연말 세금·금리 전날·일정 다음 날)은 AlarmManager.setAndAllowWhileIdle (정확 알람 아님).
//   몇 분 늦을 수는 있지만 SCHEDULE_EXACT_ALARM 권한이 필요 없고, 기기가 잠들어 있어도 울린다. WorkManager 보다 가볍고(의존성 없음)
//   시각에 더 가깝다. iOS UNCalendarNotificationTrigger 처럼 '그 시각 무렵'이면 충분한 알림이라 정확 알람을 쓰지 않는다.
// - 조건 알림(본전 도달·비중 이탈)은 iOS 처럼 앱이 켜져 시세를 갱신할 때만 본다 (백그라운드 작업 없음 → WorkManager 안 씀).
// - 예약 목록(iOS pendingNotificationRequests 자리)은 Defaults.prefs "nt.pending" 에 두고, 기기를 다시 켜면(BOOT_COMPLETED)
//   NotifyReceiver 가 다시 건다 (안드로이드 알람은 재부팅하면 사라진다). 연말 세금(매년)은 울린 뒤 다음 해로 다시 건다.
// - 알림을 누르면 MainActivity 에 NOTIFY_OPEN("naeilo.open") 글자(home·tax·rate·ext·board)를 실어 연다 (App.kt openIntent).
// ios: Data/Notifier.swift:14
object Notifier {
    // ios: Data/Notifier.swift:15
    val center: NotificationManagerCompat get() = NotificationManagerCompat.from(Defaults.ctx)
    // ios: Data/Notifier.swift:16
    private val d get() = Defaults.prefs

    /** 알림 허락 창을 띄울 화면. MainActivity.onCreate 에서 Notifier.attach(this) 를 한 번 부른다 */
    private var activity: WeakReference<ComponentActivity>? = null
    fun attach(a: ComponentActivity) { activity = WeakReference(a); ensureChannel(a.applicationContext) }
    private val reqN = AtomicInteger(0)

    enum class Status { unknown, allowed, denied }   // ios: Data/Notifier.swift:18
    // 안드로이드: 꺼져 있고 13 미만(설정에서 끔)이거나 한 번 물어봤으면 denied, 아직 안 물어봤으면 unknown
    // ios: Data/Notifier.swift:19,20
    suspend fun status(): Status {
        val ctx = Defaults.ctx
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return when {
            granted && center.areNotificationsEnabled() -> Status.allowed   // ios: Data/Notifier.swift:21
            Build.VERSION.SDK_INT < 33 || d.getBoolean("nt.asked", false) -> Status.denied   // ios: Data/Notifier.swift:22
            else -> Status.unknown   // ios: Data/Notifier.swift:23
        }
    }
    /** 처음 한 번 폰의 알림 허용 창(안드로이드 13+ POST_NOTIFICATIONS)을 띄운다. 이미 정했으면 그 결과를 돌려준다 */
    // ios: Data/Notifier.swift:27,28
    suspend fun request(): Boolean {
        if (status() == Status.allowed) return true   // ios: Data/Notifier.swift:29
        // ios: Data/Notifier.swift:30 alert·sound·badge 를 한 번에 묻는 자리: 안드로이드는 알림 권한 하나
        if (Build.VERSION.SDK_INT < 33) return false
        val act = activity?.get() ?: return false
        d.edit().putBoolean("nt.asked", true).apply()
        val got = CompletableDeferred<Boolean>()
        val l = withContext(Dispatchers.Main) {
            act.activityResultRegistry.register("nt.perm." + reqN.incrementAndGet(), ActivityResultContracts.RequestPermission()) { got.complete(it) }
                .also { it.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        return try { got.await() && center.areNotificationsEnabled() } finally { withContext(NonCancellable + Dispatchers.Main) { l.unregister() } }
    }

    /** 알림 한 통의 내용 (UNMutableNotificationContent 자리). icon = 오른쪽 그림(drawable 이름) */
    class Content {
        var title: String = ""; var body: String = ""; var sound: Boolean = false
        var userInfo: Map<String, String> = emptyMap(); var icon: String? = null
    }

    /** 설정에서 고른 앱 아이콘을 알림 오른쪽 그림으로 붙인다.
     *  알림 왼쪽 아이콘은 폰이 정하는데, 다른 아이콘을 골라도 기본 아이콘으로 나오는 경우가 있어서 그림을 같이 보낸다 */
    // ios: Data/Notifier.swift:35,36
    fun withIcon(c: Content): Content {
        val ctx = Defaults.ctx
        // ios: Data/Notifier.swift:37,38,39
        val alt = AppIconPicker.alternateIconName(ctx) ?: return c
        val prev = AppIconPicker.icons.firstOrNull { it.id == alt }?.prev ?: return c
        if (drawableId(ctx, prev) == 0) return c
        // Data/Notifier.swift:41 (검사 예외: 안드로이드는 그림을 임시 파일로 쓰지 않고 알림을 보낼 때 Bitmap 으로 바로 붙인다)
        // Data/Notifier.swift:42 (검사 예외: 위와 같음)
        // Data/Notifier.swift:43 (검사 예외: 위와 같음)
        c.icon = prev   // ios: Data/Notifier.swift:44 attachments 자리: 보낼 때 setLargeIcon
        return c   // ios: Data/Notifier.swift:45
    }

    // MARK: 예약 (설정이 바뀌거나 앱을 열 때)
    // ios: Data/Notifier.swift:49,50
    suspend fun reschedule(m: AppModel) {
        val old = pendingIds().filter { it.startsWith("rate.") }   // ios: Data/Notifier.swift:51
        remove((0 until 5).map { "morn.$it" } + listOf("dep") + old)   // ios: Data/Notifier.swift:52
        if (status() != Status.allowed) return   // ios: Data/Notifier.swift:53

        // ios: Data/Notifier.swift:55
        if (m.alerts["rate"] == true) {
            val cal = ZoneId.systemDefault()   // ios: Data/Notifier.swift:56
            // ios: Data/Notifier.swift:57
            for (e in MarketEvents.rateUpcoming(days = 90)) {
                // ios: Data/Notifier.swift:58,59
                val dd = runCatching { LocalDate.parse(e.day) }.getOrNull() ?: continue; val prev = dd.plusDays(-1)
                val at = prev.atTime(9, 0, 0).atZone(cal); if (!at.toInstant().isAfter(Instant.now())) continue
                val c = Content()   // ios: Data/Notifier.swift:60
                c.title = "내일 ${e.kind}"   // ios: Data/Notifier.swift:61
                c.body = "금리가 움직이면 내 목표는 어떻게 될까요? 금리 충격 테스트를 다시 보세요."   // ios: Data/Notifier.swift:62
                c.sound = true   // ios: Data/Notifier.swift:63
                c.userInfo = mapOf("open" to "rate")   // ios: Data/Notifier.swift:64
                val comps = at   // ios: Data/Notifier.swift:65
                add("rate.${e.id}.${e.day}", withIcon(c), at = comps)   // ios: Data/Notifier.swift:66,67
            }
        }

        // ios: Data/Notifier.swift:71
        if (m.alerts["dep"] == true) {
            val c = Content()   // ios: Data/Notifier.swift:72
            c.title = "연말 세금 정리"   // ios: Data/Notifier.swift:73
            c.body = "올해가 한 달 남았어요. 올해 해외주식 손익과 내년 5월 세금 예시를 앱에서 확인해 보세요."   // ios: Data/Notifier.swift:74
            c.sound = true   // ios: Data/Notifier.swift:75
            c.userInfo = mapOf("open" to "tax")   // ios: Data/Notifier.swift:76
            val t = nextYearly(month = 12, day = 1, hour = 9)   // ios: Data/Notifier.swift:77 repeats: 울린 뒤 다음 해로 다시 건다
            add("dep", withIcon(c), at = t, yearly = true)   // ios: Data/Notifier.swift:78
        }

        // ios: Data/Notifier.swift:81
        if (m.alerts["morn"] == true) {
            val cal = ZoneId.systemDefault()   // ios: Data/Notifier.swift:82
            var day = ZonedDateTime.now(cal)   // ios: Data/Notifier.swift:83
            var n = 0   // ios: Data/Notifier.swift:84
            // 오늘 그 시각이 지났으면 내일부터
            // ios: Data/Notifier.swift:86
            if (!day.withHour(m.alertHr).withMinute(0).withSecond(0).withNano(0).isAfter(ZonedDateTime.now(cal))) {
                day = day.plusDays(1)   // ios: Data/Notifier.swift:87
            }
            // ios: Data/Notifier.swift:89
            while (n < 5) {
                val wd = day.dayOfWeek.value % 7 + 1   // ios: Data/Notifier.swift:90 (일=1 … 토=7)
                // ios: Data/Notifier.swift:91
                if (wd != 1 && wd != 7) { val at = day.withHour(m.alertHr).withMinute(0).withSecond(0).withNano(0)
                    val c = Content()   // ios: Data/Notifier.swift:92
                    c.title = "아침 한 줄"   // ios: Data/Notifier.swift:93
                    c.body = if (n == 0) morningLine(m) else "오늘의 움직임과 ${m.keyName}까지 남은 거리를 확인해 보세요."   // ios: Data/Notifier.swift:94
                    c.sound = true   // ios: Data/Notifier.swift:95
                    c.userInfo = mapOf("open" to "home")   // ios: Data/Notifier.swift:96
                    val comps = at   // ios: Data/Notifier.swift:97
                    add("morn.$n", withIcon(c), at = comps)   // ios: Data/Notifier.swift:98,99
                    n += 1   // ios: Data/Notifier.swift:100
                }
                day = day.plusDays(1)   // ios: Data/Notifier.swift:102
            }
        }
    }

    /** 아침 한 줄: 지금 앱이 아는 숫자 (어제 장 마감 기준) */
    // ios: Data/Notifier.swift:108,109
    fun morningLine(m: AppModel): String {
        // 남은 거리 = 지금에서 몇 % 더 올라야 본전(목표)인지 (시안 알림 미리보기와 같은 식)
        val need = m.keyValue / max(1.0, m.trackValue) - 1   // ios: Data/Notifier.swift:111
        val move = "어제 하루 ${AppModel.sgn(m.todayMove)}."   // ios: Data/Notifier.swift:112
        val p1 = cformat("%.1f", need * 100)   // Data/Notifier.swift:113 (검사 예외: 보간 속 따옴표 — 다음 줄에 옮김)
        return if (need <= 0) "$move ${m.keyName}을 넘었어요." else "$move ${m.keyName}까지 $p1% 남았어요."
    }

    // MARK: 조건 알림 (시세를 갱신할 때마다)
    // ios: Data/Notifier.swift:117,118
    suspend fun check(m: AppModel) {
        if (status() != Status.allowed) return   // ios: Data/Notifier.swift:119
        val lines = ArrayList<Pair<String, String>>()   // ios: Data/Notifier.swift:120

        // 본전 도달: 본전 아래 → 위로 넘을 때 한 번
        // ios: Data/Notifier.swift:123
        if (!m.isGoal && m.cost > 0) {
            val above = m.total >= m.cost   // ios: Data/Notifier.swift:124
            // 처음 보는 날은 지금 상태만 적는다 (이미 본전 위에서 시작했으면 보내지 않는다)
            // ios: Data/Notifier.swift:126
            if (m.alerts["be"] == true && above && d.contains("nt.beAbove") && !d.getBoolean("nt.beAbove", false)) {
                lines.add("본전 도달" to "본전에 도착했어요! 평가액이 들어간 돈 ${AppModel.man(m.cost)}을 넘었어요.")   // ios: Data/Notifier.swift:127
            }
            d.edit().putBoolean("nt.beAbove", above).apply()   // ios: Data/Notifier.swift:129
        }
        // 비중 이탈: 계획 비중에서 기준 넘게 벗어나면 (하루 한 번)
        // ios: Data/Notifier.swift:132
        if (m.alerts["drift"] == true && !m.isGoal) {
            val gap = abs(m.focusWeight - m.planWeight)   // ios: Data/Notifier.swift:133
            // ios: Data/Notifier.swift:134
            if (gap * 100 > m.alertTh.toDouble()) {
                lines.add("비중 이탈" to "${m.focusName} 비중이 ${AppModel.pct(m.focusWeight)}예요. 계획(${AppModel.pct(m.planWeight)})보다 ${(gap * 100).roundToInt()}%p 벗어났어요.")   // ios: Data/Notifier.swift:135
            }
        }
        if (lines.isEmpty()) return   // ios: Data/Notifier.swift:138
        val today = Day.today   // ios: Data/Notifier.swift:139
        if (d.getString("nt.sentDay", null) == today) return    // ios: Data/Notifier.swift:140 조건 알림은 하루 한 번
        d.edit().putString("nt.sentDay", today).apply()   // ios: Data/Notifier.swift:141

        val c = Content()   // ios: Data/Notifier.swift:143
        c.title = lines.joinToString(" · ") { it.first }   // ios: Data/Notifier.swift:144
        c.body = lines.joinToString("\n") { it.second }   // ios: Data/Notifier.swift:145
        c.sound = true   // ios: Data/Notifier.swift:146
        c.userInfo = mapOf("open" to "board")   // ios: Data/Notifier.swift:147
        add("cond.$today", withIcon(c), at = null)   // ios: Data/Notifier.swift:148
    }

    /** 시안 확인용: 켠 알림을 5초 뒤에 지금 숫자로 한 번 보내 본다 */
    // ios: Data/Notifier.swift:152,153
    suspend fun test(m: AppModel) {
        if (!request()) return   // ios: Data/Notifier.swift:154
        val c = Content()   // ios: Data/Notifier.swift:155
        c.title = "naeilo 알림 시험"   // ios: Data/Notifier.swift:156
        c.body = if (m.alerts["morn"] == true) morningLine(m) else "알림이 이렇게 와요. 켠 알림은 조건이 되면 이 모양으로 와요."   // ios: Data/Notifier.swift:157
        c.sound = true   // ios: Data/Notifier.swift:158
        // 5초는 알람보다 앱 안에서 기다렸다 보내는 쪽이 정확하다 (정확 알람 권한 없이)
        // ios: Data/Notifier.swift:159,160
        val w = withIcon(c); MainScope().launch { delay(5000); post(Defaults.ctx, "test", w.title, w.body, null, w.icon) }
    }

    // ── 안드로이드 예약 도구 (UNUserNotificationCenter 자리) ──

    /** 예약해 둔 알림 하나. at = 울릴 시각(밀리초) */
    @Serializable
    class Item(val id: String, val at: Long, val title: String, val body: String, val open: String? = null, val icon: String? = null, val yearly: Boolean = false)

    private val itemMap = MapSerializer(String.serializer(), Item.serializer())
    private var pending: Map<String, Item>
        get() = d.getString("nt.pending", null)?.let { runCatching { NJson.decodeFromString(itemMap, it) }.getOrNull() } ?: emptyMap()
        set(v) { d.edit().putString("nt.pending", NJson.encodeToString(itemMap, v)).apply() }

    /** 예약해 둔 알림 이름 (pendingNotificationRequests 자리) */
    fun pendingIds(): List<String> = pending.keys.toList()

    /** 예약 지우기 (removePendingNotificationRequests 자리) */
    fun remove(ids: List<String>) {
        val p = pending; if (ids.none { it in p }) return
        val am = Defaults.ctx.getSystemService(AlarmManager::class.java)
        ids.forEach { am?.cancel(alarmIntent(Defaults.ctx, it)) }
        pending = p - ids.toSet()
    }

    /** at 이 null 이면 바로 보내고, 아니면 그 시각에 울리게 건다 */
    fun add(id: String, c: Content, at: ZonedDateTime?, yearly: Boolean = false) {
        val ctx = Defaults.ctx
        if (at == null) { post(ctx, id, c.title, c.body, c.userInfo["open"], c.icon); return }
        val it = Item(id, at.toInstant().toEpochMilli(), c.title, c.body, c.userInfo["open"], c.icon, yearly)
        pending = pending + (id to it)
        arm(ctx, it)
    }

    private fun nextYearly(month: Int, day: Int, hour: Int): ZonedDateTime {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var t = now.withMonth(month).withDayOfMonth(day).withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusYears(1)
        return t
    }

    private fun alarmIntent(ctx: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, 0, Intent(ctx, NotifyReceiver::class.java).setAction(ACTION).setData(Uri.parse("naeilo-nt:$id")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 정확 알람이 아닌 '잠들어 있어도 울리는' 알람 (SCHEDULE_EXACT_ALARM 필요 없음) */
    internal fun arm(ctx: Context, it: Item) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.at, alarmIntent(ctx, it.id))
    }

    /** 알람이 울림: 그 알림을 보내고 목록에서 뺀다 (매년 알림은 다음 해로 다시 건다) */
    internal fun fire(ctx: Context, id: String) {
        val p = pending; val it = p[id] ?: return
        post(ctx, id, it.title, it.body, it.open, it.icon)
        if (it.yearly) {
            val next = Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).plusYears(1).toInstant().toEpochMilli()
            val n = Item(it.id, next, it.title, it.body, it.open, it.icon, true)
            pending = p + (id to n); arm(ctx, n)
        } else pending = p - id
    }

    /** 기기를 다시 켜거나 앱을 새로 깔면 남은 예약을 다시 건다 (지난 것은 버린다, 매년 알림은 다음 해로) */
    internal fun rearm(ctx: Context) {
        val now = System.currentTimeMillis()
        val keep = pending.mapValues { (_, it) ->
            if (it.at > now || !it.yearly) it
            else Item(it.id, Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).plusYears(1).toInstant().toEpochMilli(), it.title, it.body, it.open, it.icon, true)
        }.filterValues { it.at > now }
        pending = keep
        keep.values.forEach { arm(ctx, it) }
    }

    const val CHANNEL = "naeilo"
    private const val ACTION = "com.naeilo.app.NOTIFY"

    /** 채널 하나: 이름 '알림'. iOS 처럼 앱이 켜져 있어도 배너로 보이게 중요도 높음 */
    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, com.naeilo.app.core.tr("알림"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = com.naeilo.app.core.tr("아침 한 줄 · 본전 도달 · 비중 이탈 · 세금 · 일정 알림")
        })
    }

    /** 지금 알림 한 통 보내기. 누르면 MainActivity 에 NOTIFY_OPEN 글자를 실어 연다 */
    @SuppressLint("MissingPermission")
    internal fun post(ctx: Context, id: String, title: String, body: String, open: String?, icon: String?) {
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val tap = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (open != null) tap.putExtra(NOTIFY_OPEN, open)
        val pi = PendingIntent.getActivity(ctx, id.hashCode(), tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ms_notifications_fill)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(pi)
        icon?.let { n -> val r = drawableId(ctx, n); if (r != 0) BitmapFactory.decodeResource(ctx.resources, r)?.let { b.setLargeIcon(it) } }
        runCatching { nm.notify(id.hashCode(), b.build()) }
    }
}

/** 알람이 울릴 때·기기를 다시 켤 때 (AndroidManifest 에 receiver 로 넣는다) */
class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Defaults.init(ctx); com.naeilo.app.core.Store.init(ctx)   // 알림 채널 이름이 앱 언어(AppModel.en)를 읽는다
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Notifier.rearm(ctx)
            else -> intent.data?.schemeSpecificPart?.let { Notifier.fire(ctx, it) }
        }
    }
}

/** 지난 일 돌아보기: 일정 전에 미리 본 범위(±금액)와 그때 평가액을 기기에 적어 두고,
 *  일정 다음 날 이후 처음 볼 때의 평가액과 비교한다. 서버 없음, 최근 10개만 둔다 */
// ios: Data/Notifier.swift:166
object EventRetro {
    @Serializable data class Snap(val kind: String, val day: String, val amt: Double, val base: Double, var after: Double? = null)   // ios: Data/Notifier.swift:167 Codable
    /** record 에 넘기는 일정 한 줄 (Swift (id:kind:day:amt:) 튜플 자리) */
    class Rec(val id: String, val kind: String, val day: String, val amt: Double)
    private const val key = "xf.retro.v1"; private const val askKey = "xf.ask.v1"   // ios: Data/Notifier.swift:168
    private val d get() = Defaults.prefs   // ios: Data/Notifier.swift:169

    private val snapMap = MapSerializer(String.serializer(), Snap.serializer())
    private val strList = ListSerializer(String.serializer())
    // ios: Data/Notifier.swift:171
    var snaps: Map<String, Snap>
        get() = d.getString(key, null)?.let { runCatching { NJson.decodeFromString(snapMap, it) }.getOrNull() } ?: emptyMap()   // ios: Data/Notifier.swift:172
        set(newValue) { d.edit().putString(key, NJson.encodeToString(snapMap, newValue)).apply() }   // ios: Data/Notifier.swift:173

    /** 일정 당일·전날에 본 값을 적는다 (일정 날까지 덮어써서 가장 가까운 값이 남는다). 지난 일정은 결과 평가액을 한 번 채운다 */
    // ios: Data/Notifier.swift:176
    fun record(evs: List<Rec>, total: Double) {
        if (!(total > 0)) return   // ios: Data/Notifier.swift:177
        val s = snaps.toMutableMap()   // ios: Data/Notifier.swift:178
        val today = Day.today   // ios: Data/Notifier.swift:179
        // ios: Data/Notifier.swift:180
        for (e in evs) { if (!(Day.dday(e.day) <= 1 && e.day >= today)) continue
            s["${e.id}.${e.day}"] = Snap(kind = e.kind, day = e.day, amt = e.amt, base = total, after = null)   // ios: Data/Notifier.swift:181
        }
        for ((k, v) in s.toMap()) if (v.after == null && v.day < today) s[k] = v.copy(after = total)   // ios: Data/Notifier.swift:183
        if (s.size > 10) for (k in s.entries.sortedBy { it.value.day }.take(s.size - 10).map { it.key }) s.remove(k)   // ios: Data/Notifier.swift:184
        snaps = s   // ios: Data/Notifier.swift:185
    }
    /** 결과까지 채워진 가장 최근 지난 일정 */
    val latest: Snap? get() = snaps.values.filter { it.after != null }.maxByOrNull { it.day }   // ios: Data/Notifier.swift:188

    private fun asked(): List<String> = d.getString(askKey, null)?.let { runCatching { NJson.decodeFromString(strList, it) }.getOrNull() } ?: emptyList()
    private fun setAsked(a: List<String>) { d.edit().putString(askKey, NJson.encodeToString(strList, a)).apply() }

    // MARK: 다음 날 결과 알림
    fun isAsked(id: String, day: String): Boolean = asked().contains("$id.$day")   // ios: Data/Notifier.swift:191
    // ios: Data/Notifier.swift:192,193
    suspend fun ask(id: String, kind: String, day: String, on: Boolean) {
        val k = "$id.$day"   // ios: Data/Notifier.swift:194
        // 이 일정과, 다음 날 알림까지 끝난 지난 일정은 목록에서 뺀다
        val a = asked().filter { it != k && (it.split(".").lastOrNull() ?: "") >= Day.today }.toMutableList()   // ios: Data/Notifier.swift:196
        Notifier.remove(listOf("xf.$k"))   // ios: Data/Notifier.swift:197
        // ios: Data/Notifier.swift:198
        if (on) {
            if (!Notifier.request()) { setAsked(a); return }   // ios: Data/Notifier.swift:199
            val cal = ZoneId.systemDefault()   // ios: Data/Notifier.swift:200
            // ios: Data/Notifier.swift:201,202
            val dd = runCatching { LocalDate.parse(day) }.getOrNull() ?: return; val next = dd.plusDays(1)
            val at = next.atTime(9, 0, 0).atZone(cal); if (!at.toInstant().isAfter(Instant.now())) return
            val c = Notifier.Content()   // ios: Data/Notifier.swift:203
            c.title = "어제 $kind 결과"   // ios: Data/Notifier.swift:204
            c.body = "내 자산이 미리 본 범위 안에서 움직였는지 '지난 일 돌아보기'에서 확인해 보세요."   // ios: Data/Notifier.swift:205
            c.sound = true   // ios: Data/Notifier.swift:206
            c.userInfo = mapOf("open" to "ext")   // ios: Data/Notifier.swift:207
            val comps = at   // ios: Data/Notifier.swift:208
            Notifier.add("xf.$k", Notifier.withIcon(c), at = comps)   // ios: Data/Notifier.swift:209,210
            a.add(k)   // ios: Data/Notifier.swift:211
        }
        setAsked(a)   // ios: Data/Notifier.swift:213
    }
}

/** 앱이 켜져 있을 때도 알림을 배너로 보여 주고, 누르면 알맞은 탭으로.
 *  안드로이드: 누르면 MainActivity 가 NOTIFY_OPEN("naeilo.open") 글자를 받는다 → App.kt openIntent → openNotification.
 *  open 을 따로 걸면 그쪽을 부른다 (didReceive). */
// ios: Data/Notifier.swift:218
object NotificationDelegate {
    val shared get() = this   // ios: Data/Notifier.swift:219
    var open: ((String) -> Unit)? = null   // ios: Data/Notifier.swift:220
    // ios: Data/Notifier.swift:221,222 willPresent: 안드로이드는 채널 중요도 높음(IMPORTANCE_HIGH)이면 앱이 켜져 있어도 배너·소리·목록으로 보인다 (Notifier.ensureChannel)
    const val presentation = NotificationManager.IMPORTANCE_HIGH

    /** 알림을 눌러 들어온 Intent 에서 열 곳 글자를 꺼내 open 에 넘긴다. 넘겼으면 true */
    // ios: Data/Notifier.swift:224
    fun didReceive(intent: Intent?): Boolean {
        // Data/Notifier.swift:225 (검사 예외: userInfo["open"] 자리가 Intent 추가값 NOTIFY_OPEN("naeilo.open"))
        val o = intent?.getStringExtra(NOTIFY_OPEN) ?: return false
        val f = open ?: return false
        intent.removeExtra(NOTIFY_OPEN); f(o)   // ios: Data/Notifier.swift:226
        return true
    }
}
