package com.naeilo.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import java.io.File
import java.net.URL
import java.time.Instant

// ios: WidgetShared/Config.swift:3
object Config {
    // ios: WidgetShared/Config.swift:4
    const val appGroup = "group.com.naeilo.widget"
    // ios: WidgetShared/Config.swift:5
    val site = URL("https://naeilo.com/")
    // ios: WidgetShared/Config.swift:6
    val data = URL("https://naeilo.com/data/")
    // ios: WidgetShared/Config.swift:7
    val relay = URL("https://asset-ai.drinker.workers.dev/")
    /** 토스증권 공용 중계 (고정 IP 서버, proxy/toss_relay.py). 사용자는 이 서버 IP 를 토스 허용 IP 로 등록한다 */
    // ios: WidgetShared/Config.swift:9
    const val tossRelay = "https://toss.naeilo.com"
    /** 공용 중계 서버의 고정 IP (Lightsail 고정 IPv4). 화면에 바로 보이고, /api/toss/ip 로 받은 값이 다르면 그 값으로 바꾼다 */
    const val tossRelayIP = "3.36.161.165"

    /** 배포 단계. 내부 베타까지만 사이트가 Yahoo 에서 모은 시세·종가·환율·계산 자료를 쓴다 (2026-10-15 결정).
     *  공개 베타·출시 빌드는 사용자 본인 키로 받은 시세와 가상 샘플(data/sample)만 쓴다.
     *  값은 gradle.properties 의 naeilo.channel 한 곳 → BuildConfig.CHANNEL. 앱 모듈 copyIosAssets 도 같은 값으로 넣을 자료를 고른다 */
    // ios: WidgetShared/Config.swift:17
    val channel: Channel = Channel.of(com.naeilo.widget.BuildConfig.CHANNEL) ?: Channel.internalBeta
    /** 내부 베타 빌드인가 (종목 목록·샘플처럼 빌드 때 정해지는 것) */
    val isInternal: Boolean get() = channel == Channel.internalBeta   // ios: WidgetShared/Config.swift:19
    /** 시험용: 내부 베타에서 Yahoo·사이트 자료를 잠시 끈다 (설정 > 시세 기준). 공개판처럼 내 키 시세만 본다 */
    val yahooOff: Boolean get() = isInternal && Store.defaults.getBoolean("yahoo_off", false)   // ios: WidgetShared/Config.swift:21
    /** 사이트가 Yahoo 로 모은 자료(data/quotes.json·prices·stats·calc 등, 추가 종목 Yahoo 중계)를 써도 되는가 */
    val usesCollectedData: Boolean get() = isInternal && !yahooOff   // ios: WidgetShared/Config.swift:23
}

// ios: WidgetShared/Config.swift:18
enum class Channel(val raw: String) {
    // ios: WidgetShared/Config.swift:19
    internalBeta("내부 베타"),
    // ios: WidgetShared/Config.swift:24
    publicBeta("공개 베타"),
    // ios: WidgetShared/Config.swift:25
    release("출시"),
    ;

    companion object {
        /** 빌드 설정 값 (internalBeta · publicBeta · release) */
        fun of(key: String?): Channel? = entries.firstOrNull { it.name == key }   // ios: WidgetShared/Config.swift:32
    }
}

/** SharedPreferences 에 없는 Double: 문자열로 둔다 (다른 형식으로 들어 있어도 숫자면 읽는다) */
fun SharedPreferences.getDouble(key: String, def: Double = 0.0): Double =
    try { getString(key, null)?.toDoubleOrNull() } catch (_: ClassCastException) { (all[key] as? Number)?.toDouble() } ?: def

fun SharedPreferences.Editor.putDouble(key: String, v: Double): SharedPreferences.Editor = putString(key, v.toString())

/** 초(1970 기준, Double) ↔ Instant */
fun Double.toInstant(): Instant = Instant.ofEpochMilli((this * 1000).toLong())
val Instant.epochSec: Double get() = toEpochMilli() / 1000.0

private val strList = ListSerializer(String.serializer())

/** 앱과 위젯이 같이 쓰는 저장소 (앱 내부 폴더의 JSON 파일). 처음에 Store.init(context) 를 한 번 부른다 */
// ios: WidgetShared/Config.swift:29
@SuppressLint("StaticFieldLeak")
object Store {
    lateinit var ctx: Context
        private set

    fun init(ctx: Context) {
        if (!this::ctx.isInitialized) this.ctx = ctx.applicationContext
    }

    // ios: WidgetShared/Config.swift:30,31,33,34,35
    val dir: File by lazy {
        val base = ctx.filesDir   // App Group 폴더 대신 앱 내부 폴더 (위젯도 같은 프로세스·같은 폴더)
        val d = File(base, "naeilo")
        d.mkdirs()
        d
    }
    // ios: WidgetShared/Config.swift:37
    fun url(name: String): File = File(dir, name)

    // ios: WidgetShared/Config.swift:39,40,41
    fun <T> read(t: KSerializer<T>, name: String): T? {
        val d = readData(name) ?: return null
        return try { NJson.decodeFromString(t, d.decodeToString()) } catch (_: Exception) { null }
    }
    inline fun <reified T> read(name: String): T? = read(serializer<T>(), name)

    // ios: WidgetShared/Config.swift:43,44
    fun <T> write(t: KSerializer<T>, v: T, name: String) {
        val d = try { NJson.encodeToString(t, v).encodeToByteArray() } catch (_: Exception) { null }
        if (d != null) writeData(d, name)
    }
    inline fun <reified T> write(v: T, name: String) = write(serializer<T>(), v, name)

    // ios: WidgetShared/Config.swift:46
    fun readData(name: String): ByteArray? = try { url(name).readBytes() } catch (_: Exception) { null }
    // ios: WidgetShared/Config.swift:47,48
    fun writeData(d: ByteArray, name: String) {
        // .atomic: 옆 파일에 다 쓴 뒤 바꿔 넣는다 (앱 내부 폴더라 따로 잠금 보호는 없다)
        try {
            val tmp = File(dir, "$name.tmp")
            tmp.writeBytes(d)
            if (!tmp.renameTo(url(name))) { url(name).writeBytes(d); tmp.delete() }
        } catch (_: Exception) {}
    }
    // ios: WidgetShared/Config.swift:50
    fun remove(name: String) { try { url(name).delete() } catch (_: Exception) {} }

    // ios: WidgetShared/Config.swift:52
    val defaults: SharedPreferences by lazy { ctx.getSharedPreferences("naeilo", Context.MODE_PRIVATE) }

    private fun strings(key: String): List<String>? =
        defaults.getString(key, null)?.let { try { NJson.decodeFromString(strList, it) } catch (_: Exception) { null } }
    private fun setStrings(key: String, v: List<String>?) {
        if (v == null) defaults.edit().remove(key).apply()
        else defaults.edit().putString(key, NJson.encodeToString(strList, v)).apply()
    }

    /** 위젯 갱신 주기 (분). 장중에만 이 주기, 장 밖에서는 3시간 */
    // ios: WidgetShared/Config.swift:55,56,57
    var intervalMin: Int
        get() { val v = defaults.getInt("intervalMin", 0); return if (v > 0) v else 30 }
        set(newValue) { defaults.edit().putInt("intervalMin", newValue).apply() }
    /** 지금 가격을 Yahoo 에서 직접 받을 기호 (내 종목 + KRW=X). 앱이 시세를 갱신할 때 써 두고 위젯도 같이 쓴다
     *  아직 앱이 써 두지 않았으면 null (그때는 사이트 값만) */
    // ios: WidgetShared/Config.swift:61,62,63
    var quoteSyms: List<String>?
        get() = strings("quoteSyms")
        set(newValue) { setStrings("quoteSyms", newValue) }
    // ios: WidgetShared/Config.swift:65,66,67
    var lastCheck: Instant?
        get() = if (defaults.contains("lastCheck")) defaults.getDouble("lastCheck").toInstant() else null
        set(newValue) { if (newValue == null) defaults.edit().remove("lastCheck").apply() else defaults.edit().putDouble("lastCheck", newValue.epochSec).apply() }
    /** ↻ 를 누른 시각. 받는 동안 위젯 버튼을 '받는 중'으로 그린다 (다 받으면 지운다) */
    // ios: WidgetShared/Config.swift:70,71,72
    var reloadingSince: Instant?
        get() = if (defaults.contains("reloadingSince")) defaults.getDouble("reloadingSince").toInstant() else null
        set(newValue) { if (newValue == null) defaults.edit().remove("reloadingSince").apply() else defaults.edit().putDouble("reloadingSince", newValue.epochSec).apply() }
    /** 받는 중인지 (12초가 지나면 끝난 것으로 본다: 받기는 8초 안에 끊긴다) */
    // ios: WidgetShared/Config.swift:75,76,77
    val isReloading: Boolean get() {
        val t = reloadingSince ?: return false
        return (Instant.now().toEpochMilli() - t.toEpochMilli()) / 1000.0 < 12
    }
    /** 마지막 ↻ 에서 시세를 하나도 새로 받지 못했는지. 위젯 아래 줄에 '받기 실패'로 보인다 (다음에 하나라도 받으면 지운다) */
    // ios: WidgetShared/Config.swift:80,81,82
    var fetchFailed: Boolean
        get() = defaults.getBoolean("fetchFailed", false)
        set(newValue) { defaults.edit().putBoolean("fetchFailed", newValue).apply() }
    /** 위젯·앱이 받아 둔 시세 파일(사이트·국내·토스·Alpaca) 중 가장 나중에 받은 시각 (초) */
    // ios: WidgetShared/Config.swift:85,86
    val newestGot: Double get() =
        listOf("site_quotes.json", "kr_quotes.json", "toss_quotes.json", "alpaca_quotes.json").mapNotNull { read(QuoteFeed.File.serializer(), it)?.got }.maxOrNull() ?: 0.0
    /** 잠금화면 위젯의 금액을 폰이 잠겨 있을 때 가린다 (기본 켬). 얼굴·지문으로 풀면 보인다 */
    // ios: WidgetShared/Config.swift:89,90,91
    var lockPrivate: Boolean
        get() = defaults.getBoolean("lockPrivate", true)
        set(newValue) { defaults.edit().putBoolean("lockPrivate", newValue).apply() }
    /** 지금 쓰는 요약이 어디서 왔는지 ("site" / "app") */
    // ios: WidgetShared/Config.swift:94,95,96
    var summarySource: String?
        get() = defaults.getString("summarySource", null)
        set(newValue) { defaults.edit().putString("summarySource", newValue).apply() }

    /** 금액 숨기기. 기본은 한 번 누르면 모든 위젯이 같이 (hideEach = false), 설정에서 위젯마다 따로로 바꿀 수 있다 */
    // ios: WidgetShared/Config.swift:100,101,102
    var hideEach: Boolean
        get() = defaults.getBoolean("hideEach", false)
        set(newValue) { defaults.edit().putBoolean("hideEach", newValue).apply() }
    // ios: WidgetShared/Config.swift:104,105,106
    var hideAll: Boolean
        get() = defaults.getBoolean("hideAll", false)
        set(newValue) { defaults.edit().putBoolean("hideAll", newValue).apply() }
    // ios: WidgetShared/Config.swift:108,109,110
    var hiddenKinds: List<String>
        get() = strings("hiddenKinds") ?: emptyList()
        set(newValue) { setStrings("hiddenKinds", newValue) }
    // ios: WidgetShared/Config.swift:112
    fun isHidden(kind: String): Boolean = if (hideEach) hiddenKinds.contains(kind) else hideAll
    // ios: WidgetShared/Config.swift:113,114,115,116,117,118
    fun toggleHidden(kind: String) {
        if (hideEach) {
            val k = hiddenKinds.toMutableList()
            val i = k.indexOf(kind); if (i >= 0) k.removeAt(i) else k.add(kind)
            hiddenKinds = k
        } else { hideAll = !hideAll }
    }

    /** 구독 중인지 (앱이 쓴다, Support.pushWidget) */
    // ios: WidgetShared/Config.swift:122,123,124
    var subOn: Boolean
        get() = defaults.getBoolean("subOn", false)
        set(newValue) { defaults.edit().putBoolean("subOn", newValue).apply() }
    /** 7일 무료가 끝나는 시각 (1970년부터 초). 0 이면 아직 모름 = 열림 */
    // ios: WidgetShared/Config.swift:127,128,129
    var trialEnd: Double
        get() = defaults.getDouble("trialEnd")
        set(newValue) { defaults.edit().putDouble("trialEnd", newValue).apply() }
    /** 위젯 전부(기본·인물·서재)는 구독 중이거나 7일 무료 동안만 보인다 (2026-10-16 결정) */
    // ios: WidgetShared/Config.swift:132
    val widgetsOpen: Boolean get() = subOn || trialEnd <= 0 || Instant.now().epochSec < trialEnd
    /** 미션 4(앱 시작 3단계)에서 실제로 끝낸 단계 (앱이 쓴다). 값이 없으면 0 = 단계 위젯은 잠김 */
    // ios: WidgetShared/Config.swift:134,135,136
    var widgetStep: Int
        get() = defaults.getInt("widgetStep", 0)
        set(newValue) { defaults.edit().putInt("widgetStep", newValue).apply() }
    /** 위젯마다 열리는 단계: 1 자산 추이·인물 자산 언덕·키 재기, 2 목표일 전망·목표 페이스·본전 진행·인물 본전 사다리·퍼즐, 3 나머지 */
    // ios: WidgetShared/Config.swift:139,140,141,142,143
    fun stepNeed(kind: String): Int = when (kind) {
        "asset.small", "lock.asset", "char.hill", "char.height" -> 1
        "future.small", "pace.small", "lock.goal", "char.ladder", "char.puzzle" -> 2
        else -> 3
    }
    // ios: WidgetShared/Config.swift:146
    fun stepOpen(kind: String): Boolean = widgetStep >= stepNeed(kind)
    // ios: WidgetShared/Config.swift:147
    fun isUnlocked(kind: String): Boolean = widgetsOpen && stepOpen(kind)
}

/** 잠긴 위젯 문구 (기본·잠금화면·인물·서재가 같이 쓴다). 미션 4 단계로 하나씩 열리고, 7일 무료 뒤에는 구독으로 연다 */
// ios: WidgetShared/Config.swift:151
object WidgetUnlock {
    // ios: WidgetShared/Config.swift:152,153,154
    fun how(kind: String): String {
        if (!Store.widgetsOpen) return WL.t("7일 무료가 끝났어요. 구독하면 다시 보여요")
        return if (WL.en) "Opens at app start step ${Store.stepNeed(kind)}" else "앱 시작 ${Store.stepNeed(kind)}단계를 하면 열려요"
    }
    // ios: WidgetShared/Config.swift:156
    val cta: String get() = if (Store.widgetsOpen) WL.t("눌러서 미션 보기 ›") else WL.t("눌러서 구독하기 ›")
}
