package com.naeilo.app.core

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 위젯 문구의 영어. 기기 언어가 한국어가 아니면 lines_en.json(assets) 의 ui 표(한국어 문구 → 영어)에서 찾는다 (tools/lines.py 가 만든다).
 *  ui 표에 없으면 앱 표 l10n_en.json 의 x(아이폰 String(localized:) 자리, tools/l10n/gen_en.py 가 위젯 모듈 글자도 넣는다)에서 찾는다.
 *  한국어 기기이거나 표에 없으면 한국어 그대로. {n} 같은 자리는 vars 로 채운다 (예: WL.t("{n}번째 블록", mapOf("n" to "3"))) */
// ios: WidgetShared/Format.swift:5
object WL {
    /** 영어 여부. 위젯은 앱과 같은 프로세스라 앱이 적어 둔 언어(appLang)를, 없으면 앱 언어를 따른다 */
    // ios: WidgetShared/Format.swift:9,11,12   (10: 확장 구분은 없음, appLang 만 읽는다)
    val en: Boolean by lazy {
        Store.defaults.getString("appLang", null)?.let { return@lazy it == "en" }
        Locale.getDefault().language == "en"
    }
    // ios: WidgetShared/Format.swift:13
    @Serializable private class File(val ui: Map<String, String>)
    // ios: WidgetShared/Format.swift:14,15,16,17,18
    private val ui: Map<String, String> by lazy {
        if (!en) return@lazy emptyMap()
        val f = try { Store.ctx.assets.open(listOf("lines_en", "json").joinToString(".")).use { NJson.decodeFromString(File.serializer(), it.readBytes().decodeToString()) } } catch (_: Exception) { null }
        f?.ui ?: emptyMap()
    }
    /** 아이폰 String(localized:) 를 옮긴 WL.t 글자의 영어: 앱 표 l10n_en.json 의 그대로 찾기(x) */
    @Serializable private class XFile(val x: Map<String, String>)
    private val xc: Map<String, String> by lazy {
        if (!en) return@lazy emptyMap()
        try { Store.ctx.assets.open("l10n_en.json").use { NJson.decodeFromString(XFile.serializer(), it.readBytes().decodeToString()).x } } catch (_: Exception) { emptyMap() }
    }
    // ios: WidgetShared/Format.swift:19,20,21,22
    fun t(ko: String, vars: Map<String, String> = emptyMap()): String {
        var s = if (en) (ui[ko] ?: xc[ko] ?: ko) else ko
        for ((k, v) in vars) s = s.replace("{$k}", v)
        return s
    }
    /** 등락 방향 한 낱말: 상승·하락 (영어 기기면 up·down) */
    // ios: WidgetShared/Format.swift:25
    fun dir(x: Double): String = t(if (x >= 0) "상승" else "하락")
}

// ios: WidgetShared/Format.swift:28
object Fmt {
    /** 5.27억. 1억이 안 되면 만원으로 (4,002만) */
    // ios: WidgetShared/Format.swift:31,32,33
    fun eok(v: Double, digits: Int = 2): String {
        if (WL.en) return krwC(v)
        return if (abs(v) < 1e8) man(v) else (if (v < 0) "−" else "") + cformat("%.${digits}f억", abs(v) / 1e8)
    }
    /** 영어: 만·억 대신 ₩2.3M 꼴 (앱 AppModel.krwC 와 같은 규칙) */
    // ios: WidgetShared/Format.swift:36,37,38,39
    fun krwC(krw: Double): String {
        val a = abs(krw)
        val u: List<Pair<Double, String>> = listOf(1e12 to "T", 1e9 to "B", 1e6 to "M", 1e3 to "K")
        val s = u.firstOrNull { a >= it.first }?.let { sig(a / it.first, 1..3) + it.second } ?: synchronized(dec) { dec.format(a.roundToLong()) }
        return (if (krw < 0) "−" else "") + "₩" + s
    }
    /** .significantDigits(1...3): 유효숫자 최대 3자리, 뒤 0 은 빼되 최소 1자리 (2.35, 12.3, 123, 1.2) */
    private fun sig(x: Double, digits: IntRange): String {
        val b = BigDecimal(x).round(MathContext(digits.last, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val out = if (b.precision() < digits.first) b.setScale(b.scale() + digits.first - b.precision()) else b
        return synchronized(sigFmt) { sigFmt.minimumFractionDigits = maxOf(0, out.scale()); sigFmt.format(out) }
    }
    private val sigFmt = DecimalFormat("#,##0.###")
    /** 499만 */
    // ios: WidgetShared/Format.swift:42,43,44
    fun man(v: Double): String {
        if (WL.en) return krwC(abs(v))
        return (try { synchronized(dec) { dec.format((abs(v) / 1e4).roundToLong()) } } catch (_: Exception) { null } ?: "0") + "만"
    }
    // 서식기는 만들 때 비싸서 한 번만 만들어 둔다 (NumberFormat 은 여러 스레드에서 같이 쓰면 안 돼서 synchronized)
    // ios: WidgetShared/Format.swift:47
    private val dec: NumberFormat = NumberFormat.getIntegerInstance()
    // ios: WidgetShared/Format.swift:48
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ko_KR".replace('_', '-')))
    // ios: WidgetShared/Format.swift:49
    private val mdhm: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.forLanguageTag("ko_KR".replace('_', '-')))
    /** +1.8% */
    // ios: WidgetShared/Format.swift:51
    fun pct(x: Double, d: Int = 1): String = (if (x > 0) "+" else if (x < 0) "−" else "") + cformat("%.${d}f%%", abs(x) * 100)
    // ios: WidgetShared/Format.swift:52
    fun arrow(x: Double): String = if (x >= 0) "▲" else "▼"
    // ios: WidgetShared/Format.swift:53
    fun comma(v: Int): String = synchronized(dec) { dec.format(v.toLong()) }
    // ios: WidgetShared/Format.swift:54,55,56
    fun time(d: Instant?): String {
        if (d == null) return "-"
        val z = d.atZone(ZoneId.systemDefault())
        return (if (z.toLocalDate() == LocalDate.now()) hm else mdhm).format(z)
    }
}

/** 미국장(프리마켓~애프터마켓) 시간: 한국 시간 평일 오후 5시 ~ 다음 날 오전 9시. 사이트 시세 수집과 같은 범위 */
// ios: WidgetShared/Format.swift:61
object MarketHours {
    /** Calendar 의 weekday 와 같게: 일=1 … 토=7 */
    private fun weekday(d: DayOfWeek): Int = d.value % 7 + 1

    // ios: WidgetShared/Format.swift:62,63,64,65,66
    fun isActive(d: Instant = Instant.now()): Boolean {
        val c = d.atZone(ZoneId.of("UTC"))   // Calendar(identifier: .gregorian)
        val wd = weekday(c.dayOfWeek); val h = c.hour // 일=1 … 토=7
        if (wd == 1 || wd == 7) return wd == 7 && h == 0 // 토요일 0시 UTC 는 금요일 장 마감 직후
        return h >= 8 || (wd != 2 && h == 0)
    }
    /** 한국 장 (평일 KST 9:00~16:00 = UTC 0~7시). 내부 테스트 국내 시세 종목이 있을 때만 갱신 시간에 넣는다 */
    // ios: WidgetShared/Format.swift:69,70,71,72
    fun isKR(d: Instant = Instant.now()): Boolean {
        val c = d.atZone(ZoneId.of("UTC"))
        val wd = weekday(c.dayOfWeek); val h = c.hour
        return wd >= 2 && wd <= 6 && h < 7
    }
    /** 다음 갱신 시각: 장중엔 설정한 주기, 장 밖엔 3시간 (그 사이 장이 열리면 그때) */
    // ios: WidgetShared/Format.swift:75,76,77,78,79,80,81
    fun nextRefresh(now: Instant = Instant.now(), kr: Boolean = false): Instant {
        val iv = (Store.intervalMin * 60).toLong()
        val open = { d: Instant -> isActive(d) || (kr && isKR(d)) }
        if (open(now)) return now.plusSeconds(iv)
        var t = now
        for (i in 0 until 36) { t = t.plusSeconds(300); if (open(t)) return t.plusSeconds(120) }
        return now.plusSeconds(3 * 3600)
    }
}

/** 위젯 정기 갱신 (기본·잠금화면·인물 위젯이 같이 쓴다): 지금 가격·환율을 위젯이 직접 받고 다음 갱신 시각을 준다.
 *  숫자는 naeilo 앱이 계산해서 Store 폴더에 써 둔다. 10분 안에 받았으면 다시 받지 않는다 (위젯이 여럿이어도 요청이 적다) */
// ios: WidgetShared/Format.swift:87
object WidgetRefresh {
    // ios: WidgetShared/Format.swift:88,89,90,91
    suspend fun fetch(): Instant {
        val before = Store.newestGot
        try {
            QuoteFeed.fetch(minAge = 600.0)
            // 내부 테스트 국내 시세 종목이 있으면 그것도 (한국 장중에도 갱신)
            // ios: WidgetShared/Format.swift:93,94,95
            val feed = Store.read(WFeed.serializer(), "feed.json")
            val ky = feed?.ky ?: emptyMap()
            KrQuoteFeed.fetch(ky, minAge = 600.0)
            // 내 토스 키가 있으면 토스 지금 가격도 위젯이 직접 (앱을 안 열어도 앱과 같은 값). 국내 종목이 있으면 한국 장중에도 갱신
            // ios: WidgetShared/Format.swift:97
            val toss = TossFeed.widgetFetch(minAge = 600.0)
            // 내 Alpaca 키가 있으면 미국 종목 지금 가격도 위젯이 직접
            // ios: WidgetShared/Format.swift:99
            AlpacaFeed.widgetFetch(minAge = 600.0)
            // 내 Longbridge 키가 있으면 그것도
            // ios: WidgetShared/Format.swift:101
            LongbridgeFeed.widgetFetch(minAge = 600.0)
            // ios: WidgetShared/Format.swift:102,103
            val tossKr = toss != null && (feed?.ty ?: emptyMap()).values.any { it.endsWith(".KS") || it.endsWith(".KQ") }
            return untilTrialEnd(MarketHours.nextRefresh(kr = ky.isNotEmpty() || tossKr))
        } finally {
            // ios: WidgetShared/Format.swift:90
            if (Store.newestGot > before) Store.fetchFailed = false
        }
    }

    /** 7일 무료가 다음 갱신보다 먼저 끝나면 그때 한 번 더 그려 바로 잠근다 (전에는 다음 갱신까지, 장 밖이면 최대 3시간 열려 있었다) */
    // ios: WidgetShared/Format.swift:107,108,109,110
    fun untilTrialEnd(next: Instant): Instant {
        val end = Store.trialEnd
        if (Store.subOn || end <= Instant.now().epochSec) return next
        return minOf(next, end.toInstant())
    }
}

/** 위젯 아래 줄의 '3분 전 · 토스'. 지금은 그 위젯 그림의 시각(타임라인 항목 시각)으로 센다 */
// ios: WidgetShared/Format.swift:115
object Fresh {
    // ios: WidgetShared/Format.swift:116,117,118,119,120,121,122
    fun ago(d: Instant?, now: Instant): String {
        if (d == null) return "-"
        val m = ((now.toEpochMilli() - d.toEpochMilli()) / 1000.0 / 60).toInt()
        if (m < 1) return WL.t("방금")
        if (m < 60) return WL.t("{n}분 전", mapOf("n" to "$m"))
        if (m < 24 * 60) return WL.t("{n}시간 전", mapOf("n" to "${m / 60}"))
        return Fmt.time(d)
    }
    /** 출처 이름 (Engine.build 의 tag). Alpaca 는 그대로 */
    // ios: WidgetShared/Format.swift:125
    fun src(feed: String?): String? = feed?.let { if (it == "Alpaca") it else WL.t(it) }
    /** '3분 전 · 토스', 마지막 ↻ 가 실패했으면 '받기 실패 · 3분 전 · 토스' */
    // ios: WidgetShared/Format.swift:127,128,129,130,131
    fun label(at: Instant?, feed: String?, now: Instant): String {
        val parts = mutableListOf(ago(at, now = now))
        src(feed)?.let { parts.add(it) }
        if (Store.fetchFailed) parts.add(0, WL.t("받기 실패"))
        return parts.joinToString(" · ")
    }
    /** 타임라인 항목 시각들: 지금부터 다음 갱신 전까지, '몇 분 전'이 바뀔 즈음마다 다시 그린다 */
    // ios: WidgetShared/Format.swift:134,135,136
    fun dates(now: Instant, until: Instant): List<Instant> {
        val mins: List<Double> = listOf(0.0, 1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0, 120.0, 180.0)
        return mins.map { now.plusMillis((it * 60 * 1000).toLong()) }.filter { it == now || it < until }
    }
}
