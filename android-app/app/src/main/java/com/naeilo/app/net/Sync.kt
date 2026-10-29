package com.naeilo.app.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.AppModel
import com.naeilo.app.Lens
import com.naeilo.app.Tab
import com.naeilo.app.core.Day
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.KeyVault
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.feedJson
import com.naeilo.app.core.getDouble
import com.naeilo.app.core.jArr
import com.naeilo.app.core.jObj
import com.naeilo.app.core.jObjs
import com.naeilo.app.core.putDouble
import com.naeilo.app.core.calc.Calm
import com.naeilo.app.core.calc.MC
import com.naeilo.app.core.calc.merge
import com.naeilo.app.core.calc.PWorld
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.isGoal
import com.naeilo.app.core.calc.siteGoal
import com.naeilo.app.core.calc.switchRoute
import com.naeilo.app.core.calc.takeSiteGoal
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Defaults
import com.naeilo.app.data.Holding
import com.naeilo.app.data.Lots
import com.naeilo.app.data.MyRecord
import com.naeilo.app.data.Progress
import com.naeilo.app.data.Sample
import com.naeilo.app.data.apply
import com.naeilo.app.ui.town.TownBook
import com.naeilo.app.ui.town.TownLog
import com.naeilo.app.ui.town.townPrefInt
import com.naeilo.app.data.progress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import com.naeilo.app.core.fastDouble
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 기기 자동 동기화: 사이트(naeilo.com 설정 > 기기 자동 동기화)와 같은 방식이다 (web/app.js esDerive·syncCall).
// - 동기화 비밀번호 → PBKDF2-SHA256(소금 "naeilo-sync-v1", 30만 번) 64바이트. 앞 32바이트의 SHA-256 앞 20바이트 = 저장 위치(id),
//   뒤 32바이트 = AES-GCM 키. 비밀번호는 저장하지 않고, 키만 Keystore 로 감싸 둔다 (KeyVault).
// - 중계(/esync?id=)에는 기기에서 암호화한 값 {c: "iv.암호문", at}만 둔다. 중계는 내용을 읽을 수 없다.
// - 나중에 고친 쪽이 이긴다 (at). 처음 켤 때 양쪽 다 값이 있으면 어느 쪽에 맞출지 묻는다.
// - 맞추는 것: 종목·수량·평균 단가(사이트 holdings 형식), 앱의 미션 진행(app 칸, 사이트는 건드리지 않고 그대로 둔다).
//   동네(주민 수첩·대화 기록·배경·산 물건·별사탕)도 app 칸에 (app.town).
//   목표(금액·목표일·시작일·매달 넣는 돈)는 양쪽이 같은 값을 쓴다 (나중에 고친 쪽이 이긴다. 회복 루트에서는 받기만 한다).
//   사이트에만 있는 칸(사건·모형 설정)과 앱이 못 다루는 종목(미국·한국 밖)은 받은 그대로 다시 올린다.
// JSON 값은 JSONSerialization 과 같은 꼴(Map·List·String·Double·Boolean·null)로 들고 다닌다 (feedJson, GoalRoute.siteGoal 과 같다).

private fun nowSec(): Double = System.currentTimeMillis() / 1000.0

/** JSONSerialization.data(withJSONObject:) 자리: Map·List·글자·숫자·참거짓·null → JSON (정수인 실수는 정수로 쓴다) */
internal fun plainToJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to plainToJson(x) })
    is List<*> -> JsonArray(v.map { plainToJson(it) })
    is Set<*> -> JsonArray(v.map { plainToJson(it) })
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Double -> if (v.isFinite() && v == Math.rint(v) && Math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    else -> JsonPrimitive(v.toString())
}
/** JSON → Map·List·글자·Double·참거짓·null (feedJson 과 같은 꼴) */
internal fun jsonToPlain(e: JsonElement?): Any? = when (e) {
    null, is JsonNull -> null
    is JsonObject -> e.mapValues { jsonToPlain(it.value) }
    is JsonArray -> e.map { jsonToPlain(it) }
    is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.content.fastDouble()
}
private fun jsonBytes(v: Any?): ByteArray = plainToJson(v).toString().encodeToByteArray()

class Sync {   // ios: Data/Sync.swift:17,18
    companion object {
        val shared: Sync by lazy { Sync() }   // ios: Data/Sync.swift:19
        const val relay = "https://asset-ai.drinker.workers.dev/esync?id="   // ios: Data/Sync.swift:20
        /** 개발자 동기화: 저장소 쓰기 권한이 있는 GitHub 토큰으로 계정별 칸 (사이트에 토큰이 있으면 사이트도 이 칸을 쓴다) */
        const val devRelay = "https://asset-ai.drinker.workers.dev/sync"   // ios: Data/Sync.swift:22

        fun krFromJSON(any: Any?): List<MC.KrDay> =   // ios: Data/Sync.swift:90
            (jArr(any) ?: emptyList()).mapNotNull { x -> jObj(x)?.let { o -> (o["d"] as? String)?.let { MC.KrDay(d = it, v = num(o["v"])) } } }   // ios: Data/Sync.swift:91
        fun num(v: Any?): Double {   // ios: Data/Sync.swift:93
            if (v is Double) return v   // ios: Data/Sync.swift:94
            if (v is Int) return v.toDouble()   // ios: Data/Sync.swift:95
            if (v is String) return v.replace(",", "").toDoubleOrNull() ?: 0.0   // ios: Data/Sync.swift:96
            return 0.0   // ios: Data/Sync.swift:97
        }

        // MARK: 비밀번호 → 저장 위치 + 키 (사이트 esDerive 와 같음)
        fun derive(password: String): Creds {   // ios: Data/Sync.swift:154
            val pw = Normalizer.normalize(password, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)   // ios: Data/Sync.swift:155
            val salt = "naeilo-sync-v1".toByteArray(Charsets.UTF_8)   // ios: Data/Sync.swift:156
            val out = ByteArray(64) { 0 }   // ios: Data/Sync.swift:157
            pbkdf2(pw, salt, 300_000, out, len = 64)   // ios: Data/Sync.swift:158,159,160
            val id = MessageDigest.getInstance("SHA-256").digest(out.copyOfRange(0, 32)).take(20).joinToString("") { "%02x".format(it.toInt() and 0xFF) }   // ios: Data/Sync.swift:162
            return Creds(id = id, k = Base64.getEncoder().encodeToString(out.copyOfRange(32, 64)))   // ios: Data/Sync.swift:163
        }
        /** CCKeyDerivationPBKDF(kCCPRFHmacAlgSHA256) 자리. 안드로이드 SecretKeyFactory 는 비밀번호 글자를 바이트로 바꾸는 방식이
         *  판마다 달라(예전 8비트) 웹·아이폰과 같은 값을 보장하려고 HMAC-SHA256 으로 직접 센다 (RFC 8018) */
        internal fun pbkdf2(pw: ByteArray, salt: ByteArray, iterations: Int, out: ByteArray, len: Int = out.size) {
            // 빈 키는 SecretKeySpec 이 받지 않는다. HMAC 은 키 뒤를 0 으로 채우므로 [0] 과 빈 키는 같은 값이다
            val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(if (pw.isEmpty()) ByteArray(1) else pw, "HmacSHA256"))
            val u = ByteArray(32); val t = ByteArray(32)
            var block = 1; var pos = 0
            while (pos < len) {
                mac.update(salt); mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
                mac.doFinal(u, 0); u.copyInto(t)
                for (i in 1 until iterations) { mac.update(u); mac.doFinal(u, 0); for (j in 0 until 32) t[j] = (t[j].toInt() xor u[j].toInt()).toByte() }
                val n = minOf(32, len - pos); t.copyInto(out, pos, 0, n); pos += n; block += 1
            }
        }
        /** AES.GCM.seal 자리: (iv 12바이트, 암호문+태그 16바이트) */
        internal fun seal(plain: ByteArray, key: ByteArray, iv: ByteArray = ByteArray(12).also { SecureRandom().nextBytes(it) }): Pair<ByteArray, ByteArray> {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return iv to c.doFinal(plain)
        }
        /** AES.GCM.open 자리. 풀 수 없으면 예외 */
        internal fun open(iv: ByteArray, all: ByteArray, key: ByteArray): ByteArray {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            return c.doFinal(all)
        }

        /** 사이트 티커 → 앱 이름 (005930.KS → 005930). 미국·한국 밖이면 null */
        fun appId(ticker: String): String? {   // ios: Data/Sync.swift:331
            val t = ticker.uppercase()   // ios: Data/Sync.swift:332
            if (t.endsWith(".KS") || t.endsWith(".KQ")) return t.dropLast(3)   // ios: Data/Sync.swift:333
            if (t.contains(".") || t.contains("=") || t.startsWith("^")) return null   // ios: Data/Sync.swift:334
            return t   // ios: Data/Sync.swift:335
        }
        /** 앱 이름 → 사이트 티커 */
        fun siteTicker(id: String): String {   // ios: Data/Sync.swift:338
            val s = Sample.symbol(id) ?: return id   // ios: Data/Sync.swift:339
            return if (s.currency == Currency.krw) CustomSymbols.yahoo(s) else id   // ios: Data/Sync.swift:340
        }
    }

    /** 일반: id + k (비밀번호로 만든 칸과 키). 개발자: token (암호화 없음, 중계가 토큰으로 계정을 확인) */
    @Serializable data class Creds(var id: String = "", var k: String = "", var token: String? = null)   // ios: Data/Sync.swift:25
    val isDev: Boolean get() = creds?.token != null   // ios: Data/Sync.swift:26
    sealed class State {   // ios: Data/Sync.swift:27
        object off : State(); object idle : State(); object working : State()
        data class ok(val at: Instant) : State(); data class failed(val why: String) : State()
    }

    var creds: Creds? by mutableStateOf(Keychain.load())   // ios: Data/Sync.swift:29
        private set
    var state: State by mutableStateOf(State.off)   // ios: Data/Sync.swift:30
    val isOn: Boolean get() = creds != null   // ios: Data/Sync.swift:31
    /** 처음 켤 때 양쪽 다 값이 있으면 고르게 한다 */
    var conflict: Remote? by mutableStateOf(null)   // ios: Data/Sync.swift:33

    data class Remote(val state: Map<String, AnyCodable>, val at: Double)   // ios: Data/Sync.swift:35

    private val d get() = Store.defaults   // ios: Data/Sync.swift:37
    /** 이 기기 값이 마지막으로 바뀐 때 (ms). 0 = 이번에 처음 켬 */
    var localAt: Double   // ios: Data/Sync.swift:39
        get() = d.getDouble("sync.localAt")   // ios: Data/Sync.swift:40
        set(newValue) { d.edit().putDouble("sync.localAt", newValue).apply() }   // ios: Data/Sync.swift:41
    /** 사이트 쪽 전체 입력값 (앱이 안 쓰는 칸을 지키려고 받은 그대로 둔다) */
    private var siteState: Map<String, Any?>   // ios: Data/Sync.swift:44
        get() = d.getString("sync.site", null)?.let { jObj(feedJson(it.encodeToByteArray())) } ?: emptyMap()   // ios: Data/Sync.swift:45
        set(newValue) { d.edit().putString("sync.site", plainToJson(newValue).toString()).apply(); siteFc = null; cashC = null }   // ios: Data/Sync.swift:46
    /** 사이트 입력값의 사건 목록·전망 설정. 앱 전망(ForecastEngine)이 그 사람의 웹과 같은 사건으로 계산한다. 받은 적이 없으면 null.
     *  동기화를 켰는지는 보지 않는다: 동기화는 값을 옮기기만 하고, 같은 값이면 켜든 끄든 같은 숫자가 나와야 한다 (저장 파일도 이 칸을 옮긴다) */
    private var siteFc: SiteForecast? = null   // ios: Data/Sync.swift:50
    val siteForecast: SiteForecast get() {   // ios: Data/Sync.swift:51
        siteFc?.let { c -> return c }   // ios: Data/Sync.swift:52
        val st = siteState   // ios: Data/Sync.swift:53
        fun <T> dec(v: Any?, t: kotlinx.serialization.KSerializer<T>): T? {   // ios: Data/Sync.swift:54
            if (v == null) return null   // ios: Data/Sync.swift:55
            return runCatching { NJson.decodeFromJsonElement(t, plainToJson(v)) }.getOrNull()   // ios: Data/Sync.swift:56
        }
        val c = SiteForecast(dec(st["events"], ListSerializer(MC.Event.serializer())), dec(st["model"], MC.Settings.serializer()))   // ios: Data/Sync.swift:58
        siteFc = c   // ios: Data/Sync.swift:59
        return c   // ios: Data/Sync.swift:60
    }
    /** 사이트 입력값의 현금 (원화, 달러). 앱은 입력 칸 없이 받은 값만 쓴다 (내 길·전망에 웹과 같은 식으로). 받은 적이 없으면 0 */
    data class Cash(val krw: Double, val usd: Double)
    private var cashC: Cash? = null   // ios: Data/Sync.swift:63
    val siteCash: Cash get() {   // ios: Data/Sync.swift:64
        cashC?.let { c -> return c }   // ios: Data/Sync.swift:65
        val o = jObj(siteState["cash"])   // ios: Data/Sync.swift:66
        val c = Cash(krw = num(o?.get("krw")), usd = num(o?.get("usd")))   // ios: Data/Sync.swift:67
        cashC = c   // ios: Data/Sync.swift:68
        return c   // ios: Data/Sync.swift:69
    }
    /** 저장 파일로 옮기는 계산 칸 (사건·전망 설정·현금). 받은 적이 없으면 빈 묶음이라 불러온 폰도 비운다 */
    val calcKeys = listOf("events", "model", "cash")   // ios: Data/Sync.swift:72
    val calcSite: ByteArray get() = plainToJson(siteState.filterKeys { it in calcKeys }).toString().encodeToByteArray()   // ios: Data/Sync.swift:73
    fun restoreCalcSite(data: ByteArray) {   // ios: Data/Sync.swift:74
        val o = jObj(feedJson(data)) ?: return   // ios: Data/Sync.swift:75
        val st = siteState.toMutableMap()   // ios: Data/Sync.swift:76
        for (k in calcKeys) { val v = o[k]; if (v == null) st.remove(k) else st[k] = v }   // ios: Data/Sync.swift:77
        siteState = st   // ios: Data/Sync.swift:78
    }
    /** 받을 때 들여오지 않은 사이트 줄 (보낼 때 그대로 돌려보낸다) */
    private var keepTickers: Set<String>   // ios: Data/Sync.swift:81
        get() = d.getString("sync.keep", null)?.let { runCatching { NJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }?.toSet() ?: emptySet()   // ios: Data/Sync.swift:82
        set(newValue) { d.edit().putString("sync.keep", NJson.encodeToString(ListSerializer(String.serializer()), newValue.toList())).apply() }   // ios: Data/Sync.swift:83
    /** 사이트에 단가가 없어서 지금 가격으로 채운 종목 (그대로면 사이트에는 비워 둔 채로 보낸다) */
    private var subAvg: Map<String, Double>   // ios: Data/Sync.swift:86
        get() = d.getString("sync.subAvg", null)?.let { runCatching { NJson.decodeFromString(MapSerializer(String.serializer(), Double.serializer()), it) }.getOrNull() } ?: emptyMap()   // ios: Data/Sync.swift:87
        set(newValue) { d.edit().putString("sync.subAvg", NJson.encodeToString(MapSerializer(String.serializer(), Double.serializer()), newValue)).apply() }   // ios: Data/Sync.swift:88
    private var lastSig: String?   // ios: Data/Sync.swift:99
        get() = d.getString("sync.sig", null)   // ios: Data/Sync.swift:100
        set(newValue) { d.edit().putString("sync.sig", newValue).apply() }   // ios: Data/Sync.swift:101

    init { state = if (creds == null) State.off else State.idle }   // ios: Data/Sync.swift:104

    // MARK: 켜기·끄기

    /** 비밀번호로 켠다. 30만 번 계산이라 1초쯤 걸린다 (계산은 Dispatchers.Default) */
    suspend fun turnOn(password: String, model: AppModel) {   // ios: Data/Sync.swift:109,110
        state = State.working   // ios: Data/Sync.swift:111
        val c = withContext(Dispatchers.Default) { derive(password) }   // ios: Data/Sync.swift:112
        Keychain.save(c)   // ios: Data/Sync.swift:113
        creds = c   // ios: Data/Sync.swift:114
        localAt = 0.0   // ios: Data/Sync.swift:115
        lastSig = null   // ios: Data/Sync.swift:116
        pull(model)   // ios: Data/Sync.swift:117
    }

    /** 개발자 토큰으로 켠다. 중계가 저장소 쓰기 권한을 확인하고, 아니면 켜지지 않는다 */
    suspend fun turnOnDev(token: String, model: AppModel) {   // ios: Data/Sync.swift:121,122
        val t = token.trim()   // ios: Data/Sync.swift:123
        if (t.isEmpty() || !t.all { it.code > 0x20 && it.code < 0x7f }) {   // ios: Data/Sync.swift:124
            state = State.failed("토큰에 다른 글자가 섞여 있어요. 다시 붙여넣어 주세요"); return   // ios: Data/Sync.swift:125
        }
        state = State.working   // ios: Data/Sync.swift:127
        creds = Creds(token = t)   // ios: Data/Sync.swift:128
        try { call() } catch (e: CancellationException) { throw e } catch (e: Exception) {   // ios: Data/Sync.swift:129
            creds = null   // ios: Data/Sync.swift:130
            state = State.failed(e.message ?: "")   // ios: Data/Sync.swift:131
            return   // ios: Data/Sync.swift:132
        }
        Keychain.save(creds!!)   // ios: Data/Sync.swift:134
        localAt = 0.0   // ios: Data/Sync.swift:135
        lastSig = null   // ios: Data/Sync.swift:136
        pull(model)   // ios: Data/Sync.swift:137
    }

    fun turnOff() {   // ios: Data/Sync.swift:140,141
        Keychain.delete()   // ios: Data/Sync.swift:142
        creds = null   // ios: Data/Sync.swift:143
        conflict = null   // ios: Data/Sync.swift:144
        pulledOnce = false   // ios: Data/Sync.swift:145
        dirty = false   // ios: Data/Sync.swift:146
        state = State.off   // ios: Data/Sync.swift:147
        localAt = 0.0   // ios: Data/Sync.swift:148
        lastSig = null   // ios: Data/Sync.swift:149
    }

    private fun key(): ByteArray {   // ios: Data/Sync.swift:166
        val c = creds; val k = c?.k?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }; if (c == null || c.token != null || k == null) throw SyncError.off()   // ios: Data/Sync.swift:167
        return k   // ios: Data/Sync.swift:168
    }

    sealed class SyncError(msg: String) : Exception(msg) {   // ios: Data/Sync.swift:171,173,174
        class off : SyncError("동기화가 꺼져 있어요")   // ios: Data/Sync.swift:172,175
        class wrongPassword : SyncError("풀 수 없어요 (다른 기기와 같은 비밀번호인지 확인해 주세요)")   // ios: Data/Sync.swift:176
        class server(s: String) : SyncError(s)   // ios: Data/Sync.swift:177
    }

    // MARK: 중계와 주고받기 (사이트 syncCall 과 같음)

    data class Reply(val state: Map<String, Any?>?, val at: Double, val stale: Boolean)
    private suspend fun call(post: Map<String, Any?>? = null): Reply = withContext(Dispatchers.IO) {   // ios: Data/Sync.swift:184
        val c = creds ?: throw SyncError.off(); val url = if (c.token != null) devRelay else relay + c.id   // ios: Data/Sync.swift:185
        val req = FeedReq(url, timeout = 20.0)   // ios: Data/Sync.swift:186
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: Data/Sync.swift:187
        val t = c.token
        if (t != null) {   // ios: Data/Sync.swift:188
            // 개발자 칸: 암호화 없이 {state, at} (사이트 개발자 동기화와 같음)
            req.setValue("Bearer " + t, "Authorization")   // ios: Data/Sync.swift:190
            val st = post?.get("state"); if (post != null && st != null) {   // ios: Data/Sync.swift:191
                req.httpMethod = "POST"   // ios: Data/Sync.swift:192
                req.setValue("application/json", "Content-Type")   // ios: Data/Sync.swift:193
                req.httpBody = jsonBytes(mapOf("state" to st, "at" to (post["at"] ?: nowSec() * 1000)))   // ios: Data/Sync.swift:194
            }
        } else if (post != null && post["state"] != null) {   // ios: Data/Sync.swift:196
            val st = post["state"]
            val plain = jsonBytes(st)   // ios: Data/Sync.swift:197
            val (iv, ct) = seal(plain, key())   // ios: Data/Sync.swift:198,199
            val b64 = Base64.getEncoder()
            val body: Map<String, Any?> = mapOf("c" to b64.encodeToString(iv) + "." + b64.encodeToString(ct), "at" to (post["at"] ?: nowSec() * 1000))   // ios: Data/Sync.swift:200
            req.httpMethod = "POST"   // ios: Data/Sync.swift:201
            req.setValue("application/json", "Content-Type")   // ios: Data/Sync.swift:202
            req.httpBody = jsonBytes(body)   // ios: Data/Sync.swift:203
        }
        val (code, data) = req.send()   // ios: Data/Sync.swift:205
        val j = jObj(feedJson(data)) ?: emptyMap()   // ios: Data/Sync.swift:206
        if (code != 200) throw SyncError.server((j["error"] as? String) ?: "중계 응답 ${code}")   // ios: Data/Sync.swift:207
        val at = (j["at"] as? Double) ?: 0.0   // ios: Data/Sync.swift:208
        val ds = jObj(j["state"]); if (c.token != null && ds != null) {   // ios: Data/Sync.swift:209
            return@withContext Reply(ds - "_w", at, false)   // ios: Data/Sync.swift:210,211
        }
        val cs = j["c"] as? String   // ios: Data/Sync.swift:213
        if (cs != null) {
            val parts = cs.split(".")   // ios: Data/Sync.swift:214
            val dec = Base64.getDecoder()
            val iv = parts.getOrNull(0)?.let { runCatching { dec.decode(it) }.getOrNull() }; val all = parts.getOrNull(1)?.let { runCatching { dec.decode(it) }.getOrNull() }   // ios: Data/Sync.swift:215
            if (parts.size != 2 || iv == null || all == null || all.size <= 16) throw SyncError.wrongPassword()
            try {   // ios: Data/Sync.swift:216
                val plain = open(iv, all, key())   // ios: Data/Sync.swift:218
                val st2 = jObj(feedJson(plain)) ?: emptyMap()   // ios: Data/Sync.swift:219
                return@withContext Reply(st2 - "_w", at, false)      // 사이트가 올린 위젯 요약은 입력값이 아니다   // ios: Data/Sync.swift:220,221
            } catch (e: Exception) { throw SyncError.wrongPassword() }   // ios: Data/Sync.swift:222
        }
        Reply(null, at, (j["stale"] as? Boolean) ?: false)   // ios: Data/Sync.swift:224
    }

    // MARK: 받기·보내기

    /** 앱을 열 때·켤 때: 중계 값이 더 새것이면 이 기기를 맞추고, 이 기기가 더 새것이면 보낸다 */
    suspend fun pull(m: AppModel) {   // ios: Data/Sync.swift:230,231
        if (!isOn) return   // ios: Data/Sync.swift:232
        state = State.working   // ios: Data/Sync.swift:233
        try {   // ios: Data/Sync.swift:234
            val r = call()   // ios: Data/Sync.swift:235
            // 지난 날 수량 기록은 언제나 사이트 값으로 (이 기기 값이 더 새로워 받지 않을 때도: 앱에만 남은 예전 줄이 내 길 시작값을 웹과 다르게 만든다)
            val st = r.state
            if (st != null && !(localAt == 0.0 && hasLocalData(m))) {   // ios: Data/Sync.swift:237
                val L = Lots.withSite(Lots.fromJSON(st["lots"]?.let(::plainToJson)), m.lots)   // ios: Data/Sync.swift:238
                if (L != m.lots) m.lots = L   // ios: Data/Sync.swift:239
            }
            // 투자 습관 기록은 언제나 합친다 (어느 쪽이 더 새것이든 다른 기기에만 있는 매매를 잃지 않게)
            (st?.get("app") as? Map<*, *>)?.get("calm")?.let { p ->   // ios: Data/Sync.swift:242
                runCatching { NJson.decodeFromJsonElement(Calm.serializer(), plainToJson(p)) }.getOrNull()?.let { c ->   // ios: Data/Sync.swift:243
                    val mc = Calm.merge(m.calm, c)   // ios: Data/Sync.swift:244
                    if (mc != m.calm) m.calm = mc   // ios: Data/Sync.swift:245
                }
            }
            // 계산 자료(전망 입력값)는 언제나 더 새 한 벌로 (어느 쪽이 더 새것이든 두 기기가 같은 입력으로 센다)
            val rc = (st?.get("app") as? Map<*, *>)?.get("calc")?.let { p -> runCatching { NJson.decodeFromJsonElement(CalcSnap.serializer(), plainToJson(p)) }.getOrNull() }
            if (rc != null) Market.shared.takeCalc(rc)
            val sendCalc = ownCalcNewer(rc?.asOf)
            if (st != null && r.at > localAt) {   // ios: Data/Sync.swift:247
                if (localAt == 0.0 && hasLocalData(m)) {   // ios: Data/Sync.swift:248
                    // 처음 켤 때 양쪽 다 값이 있으면 묻는다 (사이트와 같은 동작)
                    conflict = Remote(state = st.mapValues { AnyCodable(it.value) }, at = r.at)   // ios: Data/Sync.swift:250
                    state = State.idle   // ios: Data/Sync.swift:251
                    return   // ios: Data/Sync.swift:252
                }
                apply(st, at = r.at, m = m)   // ios: Data/Sync.swift:254
                pulledOnce = true   // ios: Data/Sync.swift:255
            } else if (r.state == null || r.at < localAt || localAt == 0.0) {   // ios: Data/Sync.swift:256
                if (localAt == 0.0) localAt = nowSec() * 1000   // ios: Data/Sync.swift:257
                pulledOnce = true   // ios: Data/Sync.swift:258
                push(m)   // ios: Data/Sync.swift:259
                return   // ios: Data/Sync.swift:260
            }
            pulledOnce = true   // ios: Data/Sync.swift:262
            // 이 기기 계산 자료가 더 새면 바로 올린다 (다른 기기가 이 한 벌로 센다)
            if (sendCalc && conflict == null) { localAt = nowSec() * 1000; push(m); return }
            state = State.ok(Instant.now())   // ios: Data/Sync.swift:263
        } catch (e: CancellationException) { throw e } catch (e: Exception) { state = State.failed(e.message ?: "") }   // ios: Data/Sync.swift:264
    }

    /** 이 기기가 센 계산 자료가 받은 것(asOf)보다 새로운지 */
    private fun ownCalcNewer(asOf: String?): Boolean {
        val mk = Market.shared
        return mk.calcInUse == null && mk.ownStats != null && mk.ownAsOf > (asOf ?: "")
    }
    /** 이 기기 일봉이 새로 들어와 계산 자료가 더 새졌을 때 (App.kt): 바로 올린다 */
    fun calcChanged(m: AppModel) {
        if (!(isOn && conflict == null && pulledOnce && ownCalcNewer(Market.shared.calcSnap?.asOf))) return
        localAt = nowSec() * 1000
        MainScope().launch {
            try { push(m) } catch (e: CancellationException) { throw e } catch (e: Exception) { dirty = true; state = State.failed(e.message ?: "") }
        }
    }

    /** 처음 켤 때 고른 쪽으로 */
    suspend fun resolve(useRemote: Boolean, m: AppModel) {   // ios: Data/Sync.swift:268,269
        val c = conflict ?: return   // ios: Data/Sync.swift:270
        conflict = null   // ios: Data/Sync.swift:271
        state = State.working   // ios: Data/Sync.swift:272
        try {   // ios: Data/Sync.swift:273
            pulledOnce = true   // ios: Data/Sync.swift:274
            if (useRemote) { apply(c.state.mapValues { it.value.value }, at = c.at, m = m); state = State.ok(Instant.now()) }   // ios: Data/Sync.swift:275
            else {   // ios: Data/Sync.swift:276
                siteState = c.state.mapValues { it.value.value }      // 사이트 칸은 지키고 종목·진행만 이 기기 값으로   // ios: Data/Sync.swift:277
                localAt = nowSec() * 1000   // ios: Data/Sync.swift:278
                push(m)   // ios: Data/Sync.swift:279
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { state = State.failed(e.message ?: "") }   // ios: Data/Sync.swift:281
    }

    /** 이 기기 값이 바뀌었을 때: 표시만 해 두고 올리기는 앱을 닫을 때 한 번 (flush) */
    private var dirty = false   // ios: Data/Sync.swift:285
    private var pulledOnce = false   // ios: Data/Sync.swift:286
    fun changed(m: AppModel) {   // ios: Data/Sync.swift:287,288
        // 이번에 앱을 켠 뒤 한 번은 받아 본 다음에만 보낸다 (먼저 보내면 다른 기기의 새 값을 덮을 수 있다)
        if (!(isOn && conflict == null && pulledOnce)) return   // ios: Data/Sync.swift:290
        val sig = signature(m)   // ios: Data/Sync.swift:291
        if (sig == lastSig) return   // ios: Data/Sync.swift:292
        localAt = nowSec() * 1000   // ios: Data/Sync.swift:293
        dirty = true   // ios: Data/Sync.swift:294
    }

    /** 앱이 뒤로 갈 때 (MainActivity.onStop): 바뀐 값이 있으면 한 번만 올린다. 프로세스는 바로 안 죽어서 짧은 올리기는 끝난다 */
    fun flush(m: AppModel) {   // ios: Data/Sync.swift:298,299
        changed(m)      // 동네는 진행·종목 밖이라 닫을 때 한 번 더 본다   // ios: Data/Sync.swift:300
        if (!(dirty && isOn && conflict == null && pulledOnce)) return   // ios: Data/Sync.swift:301
        dirty = false   // ios: Data/Sync.swift:302
        // ios: Data/Sync.swift:306,307 (beginBackgroundTask 는 안드로이드에 없어 MainScope 로만)
        MainScope().launch {
            try { push(m) } catch (e: CancellationException) { throw e } catch (e: Exception) { dirty = true; state = State.failed(e.message ?: "") }
        }
    }

    private suspend fun push(m: AppModel) {   // ios: Data/Sync.swift:312,313
        state = State.working   // ios: Data/Sync.swift:314
        val st = outgoing(m)   // ios: Data/Sync.swift:315
        val r = call(post = mapOf("state" to st, "at" to localAt))   // ios: Data/Sync.swift:316
        if (r.stale) { pull(m); return }      // 다른 기기가 더 새 값을 올렸다   // ios: Data/Sync.swift:317
        siteState = st   // ios: Data/Sync.swift:318
        lastSig = signature(m)   // ios: Data/Sync.swift:319
        state = State.ok(Instant.now())   // ios: Data/Sync.swift:320
    }

    // MARK: 앱 ↔ 사이트 형식

    /** 이 기기에 지킬 값이 있는지: 첫 질문을 마쳤고 종목이나 미션 진행이 있으면 */
    private fun hasLocalData(m: AppModel): Boolean =   // ios: Data/Sync.swift:326
        m.onboarded && (m.holdings.filter { !Sample.isVirtual(it.symbol) }.isNotEmpty() || m.done.isNotEmpty() || m.gDone.isNotEmpty())   // ios: Data/Sync.swift:327

    internal fun outgoing(m: AppModel): Map<String, Any?> {   // ios: Data/Sync.swift:343
        val st = siteState.toMutableMap()   // ios: Data/Sync.swift:344
        if (st["version"] == null) st["version"] = 1   // ios: Data/Sync.swift:345
        val old = jObjs(st["holdings"]) ?: emptyList()   // ios: Data/Sync.swift:346
        val held = m.holdings.map { it.symbol }.toSet(); val keep = keepTickers; val sub = subAvg   // ios: Data/Sync.swift:347
        // 사이트 줄 순서를 지킨다: 그대로 두는 줄(앱이 못 다루는 종목, 받을 때 들여오지 않은 줄)은 그대로,
        // 앱에 있는 종목은 바뀐 칸만 고치고, 앱에서 지운 종목은 뺀다. 앱에서 새로 넣은 종목은 끝에 붙인다
        fun updated(h: Holding, base: Map<String, Any?>?): MutableMap<String, Any?> {   // ios: Data/Sync.swift:350
            val row: MutableMap<String, Any?> = base?.toMutableMap() ?: mutableMapOf("note" to "", "price" to null)   // ios: Data/Sync.swift:351
            if (num(row["shares"]) != h.qty) row["shares"] = h.qty   // ios: Data/Sync.swift:352
            val oldAvg = num(row["avg_cost"])   // ios: Data/Sync.swift:353
            val s = sub[h.symbol]
            if (oldAvg == 0.0 && s != null && Math.abs(s - h.avg) < 1e-9) { /* 사이트에 단가가 없던 줄: 비워 둔 그대로 */ }   // ios: Data/Sync.swift:354
            else if (oldAvg != h.avg) row["avg_cost"] = h.avg   // ios: Data/Sync.swift:355
            row["ticker"] = (row["ticker"] as? String) ?: siteTicker(h.symbol)   // ios: Data/Sync.swift:356
            // 한국 종목: 넣은 가격과 그날 (웹 표의 가격 칸과 같은 값)
            val c = CustomSymbols.shared.saved(h.symbol); val p = c?.kp   // ios: Data/Sync.swift:358
            if (c != null && !c.usd && p != null) {
                row["price"] = p; row["price_date"] = c.kd ?: Day.today   // ios: Data/Sync.swift:359
                if (((row["name"] as? String) ?: "").isEmpty()) row["name"] = c.name   // ios: Data/Sync.swift:360
            }
            return row   // ios: Data/Sync.swift:362
        }
        val hs = ArrayList<Map<String, Any?>>(); val used = HashSet<String>()   // ios: Data/Sync.swift:364
        for (row in old) {   // ios: Data/Sync.swift:365
            val t = row["ticker"] as? String; val id = t?.let { appId(it) }; if (t == null || id == null) { hs.add(row); continue }   // ios: Data/Sync.swift:366
            val h = m.holdings.firstOrNull { it.symbol == id }
            if (h != null && !used.contains(id)) { hs.add(updated(h, row)); used.add(id) }   // ios: Data/Sync.swift:367
            else if (keep.contains(t.uppercase()) && !held.contains(id)) { hs.add(row) }   // ios: Data/Sync.swift:368
        }
        for (h in m.holdings) { if (Sample.isVirtual(h.symbol) || used.contains(h.symbol)) continue   // ios: Data/Sync.swift:370
            val row = updated(h, null); row["ticker"] = siteTicker(h.symbol); hs.add(row)   // ios: Data/Sync.swift:371
        }
        st["holdings"] = hs   // ios: Data/Sync.swift:373
        // 앱만 쓰는 칸: 미션 진행과 가상 종목(DRNK). 사이트는 이 칸을 읽지 않고 그대로 둔다
        val app = mutableMapOf<String, Any?>("v" to 1)   // ios: Data/Sync.swift:375
        runCatching { jsonToPlain(NJson.encodeToJsonElement(Progress.serializer(), m.progress)) }.getOrNull()?.let { p -> app["progress"] = p }   // ios: Data/Sync.swift:376
        app["virtual"] = m.holdings.filter { Sample.isVirtual(it.symbol) }.map { mapOf("t" to it.symbol, "q" to it.qty, "a" to it.avg) }   // ios: Data/Sync.swift:377
        // 동네: 주민 수첩(이름·모자·모습)·대화 기록(한 줄 답)·배경·산 물건·별사탕 (앱만 쓴다)
        townOut((st["app"] as? Map<*, *>)?.get("town"))?.let { t -> app["town"] = t }   // ios: Data/Sync.swift:379
        // 평행 세계의 나: 연 날·바구니·인물·본선 기록 (앱만 쓴다)
        m.pworld?.let { w -> runCatching { jsonToPlain(NJson.encodeToJsonElement(PWorld.serializer(), w)) }.getOrNull()?.let { j -> app["parallel"] = j } }   // ios: Data/Sync.swift:381
        // 내 투자 습관 기록: 두 기기가 같은 지수를 내도록 (홈 소식은 기기마다 따로라 빼고)
        runCatching { jsonToPlain(NJson.encodeToJsonElement(Calm.serializer(), m.calm.copy(notes = null))) }.getOrNull()?.let { j -> app["calm"] = j }   // ios: Data/Sync.swift:383,384
        // 계산 자료(전망 입력값): 기기마다 받은 시각이 달라도 같은 숫자가 나오게 둘 중 더 새 한 벌을 함께 쓴다
        Market.shared.calcOut?.let { c -> runCatching { jsonToPlain(NJson.encodeToJsonElement(CalcSnap.serializer(), c)) }.getOrNull()?.let { j ->
            app["calc"] = j
            // 올린 한 벌을 이 기기도 쓴다 (이 기기 값은 원/달러 오늘 값 등으로 계속 바뀌어, 받은 기기와 다시 갈라지지 않게)
            if (Market.shared.calcInUse == null) Market.shared.takeCalc(c)
        } }
        st["app"] = app   // ios: Data/Sync.swift:385
        // 목표는 사이트와 같은 칸(goal)에. 회복 루트(본전 기준)와 사이트 샘플 중에는 사이트 목표를 그대로 둔다
        if (m.isGoal && (st["sample"] as? Boolean) != true) st["goal"] = m.siteGoal(jObj(st["goal"]))   // ios: Data/Sync.swift:387
        // 내 길(회복·목표)은 웹 홈 첫 질문과 같은 칸(route)에. 목표 루트(플러스·처음)는 웹에서 "goal"
        if ((st["sample"] as? Boolean) != true) st["route"] = if (m.isGoal) "goal" else "recover"   // ios: Data/Sync.swift:389
        // 전망 관점(현재 정세·내 관점·과거 추세)과 추세 신뢰는 웹 전망 설정과 같은 칸(model.scenario·trust)에. 웹이 다른 시나리오(보수 등)면 그대로 둔다
        val md = jObj(st["model"])?.toMutableMap() ?: mutableMapOf()   // ios: Data/Sync.swift:391
        if (listOf("blend", "base", "smooth", null).contains(md["scenario"] as? String)) {   // ios: Data/Sync.swift:392
            md["scenario"] = if (m.lens == Lens.base) "base" else if (m.lens == Lens.smooth) "smooth" else "blend"   // ios: Data/Sync.swift:393
            md["trust"] = m.trust   // ios: Data/Sync.swift:394
            st["model"] = md   // ios: Data/Sync.swift:395
        }
        // 내 기록은 사이트와 같은 칸(daily)에 날짜별로 합쳐 둔다
        val daily = MyRecord.merge(MyRecord.fromJSON(st["daily"]?.let(::plainToJson)), m.myRecord)   // ios: Data/Sync.swift:398
        if (daily.isNotEmpty()) MyRecord.toJSON(daily)?.let { j -> st["daily"] = jsonToPlain(j) }   // ios: Data/Sync.swift:399
        // 수량 기록은 사이트와 같은 칸(lots)에 날짜별로 합쳐 둔다 (내 길 실제 평가액을 웹과 같은 수량으로)
        // 지난 날은 사이트 값만 (앱에만 남은 예전 줄이 웹과 다른 시작값을 만들지 않게), 오늘 줄은 이 기기 값
        val lots = Lots.withSite(Lots.fromJSON(st["lots"]?.let(::plainToJson)), m.lots)   // ios: Data/Sync.swift:402
        if (lots.isNotEmpty()) Lots.toJSON(lots)?.let { j -> st["lots"] = jsonToPlain(j) }   // ios: Data/Sync.swift:403
        // 국내 주식 합계 기록도 같은 칸(kr_log)에 날짜별로 합친다 (같은 날은 이 기기 값)
        val kr = MC.krNorm(krFromJSON(st["kr_log"]) + CustomSymbols.shared.krLog)   // ios: Data/Sync.swift:405
        if (kr.isNotEmpty()) st["kr_log"] = kr.map { mapOf("d" to it.d, "v" to it.v) }   // ios: Data/Sync.swift:406
        st.remove("ui")   // ios: Data/Sync.swift:407
        st.remove("_w")   // ios: Data/Sync.swift:408
        return st   // ios: Data/Sync.swift:409
    }

    private suspend fun apply(st: Map<String, Any?>, at: Double, m: AppModel) {   // ios: Data/Sync.swift:412,413
        var hs = ArrayList<Holding>()   // ios: Data/Sync.swift:414
        val unknown = ArrayList<StockHit>()   // ios: Data/Sync.swift:415
        val keep = HashSet<String>(); val sub = HashMap<String, Double>(); val krPrice = LinkedHashMap<String, Pair<Double, String?>>()   // ios: Data/Sync.swift:416
        for (h in jObjs(st["holdings"]) ?: emptyList()) {   // ios: Data/Sync.swift:417
            val t = h["ticker"] as? String; val id = t?.let { appId(it) }; if (t == null || id == null) continue   // ios: Data/Sync.swift:418
            val q = num(h["shares"]); val a = num(h["avg_cost"])   // ios: Data/Sync.swift:419
            if (!(q > 0)) { keep.add(t.uppercase()); continue }   // ios: Data/Sync.swift:420
            val kr = t.uppercase().endsWith(".KS") || t.uppercase().endsWith(".KQ")   // ios: Data/Sync.swift:421
            if (kr && num(h["price"]) > 0) krPrice[id] = Pair(num(h["price"]), h["price_date"] as? String)   // ios: Data/Sync.swift:422
            if (CustomSymbols.shared.symbol(id) == null && YahooSample.quotes[id] == null) {   // ios: Data/Sync.swift:423
                val ko = StockCatalog.all.firstOrNull { it.id == id }   // ios: Data/Sync.swift:424
                unknown.add(StockHit(id = id, yahoo = if (kr) t.uppercase() else id, name = ko?.name ?: Sample.symbols.firstOrNull { it.id == id }?.name ?: (if (kr) KrxList.name(id) else null) ?: (h["name"] as? String)?.let { if (it.isEmpty()) null else it } ?: id,   // ios: Data/Sync.swift:425
                    market = ko?.market ?: (if (kr) (if (t.uppercase().endsWith(".KQ")) "코스닥" else "코스피") else "미국 주식"),   // ios: Data/Sync.swift:426
                    currency = if (kr) Currency.krw else Currency.usd))   // ios: Data/Sync.swift:427
            }
            // 평균 단가가 없으면(사이트는 선택 항목) 지금 가격으로 둔다
            hs.add(Holding(symbol = id, qty = q, avg = a))   // ios: Data/Sync.swift:430
        }
        // 처음 보는 종목은 시세·3년 종가를 받는다 (못 받으면 그 종목은 빼고 알린다)
        val skipped = ArrayList<String>()   // ios: Data/Sync.swift:433
        for (u in unknown) {   // ios: Data/Sync.swift:434
            try { CustomSymbols.shared.fetch(u) } catch (e: CancellationException) { throw e } catch (e: Exception) {   // ios: Data/Sync.swift:435
                skipped.add(u.id); hs.removeAll { it.symbol == u.id }   // ios: Data/Sync.swift:436
                keep.add(siteTicker(u.id).uppercase()); keep.add(u.yahoo.uppercase())   // ios: Data/Sync.swift:437
            }
        }
        // 한국 종목: 웹에 넣은 가격을 그대로 (가격이 없으면 종목 화면에서 넣게 둔다)
        for ((id, v) in krPrice) CustomSymbols.shared.setPrice(id, v.first, date = v.second)   // ios: Data/Sync.swift:441
        CustomSymbols.shared.mergeKr(krFromJSON(st["kr_log"]))     // 국내 주식 합계 날짜별 기록 (같은 날은 받은 쪽)   // ios: Data/Sync.swift:442
        hs = ArrayList(hs.map { h ->   // ios: Data/Sync.swift:443
            val s = if (h.avg <= 0) Sample.symbol(h.symbol) else null; if (s == null) return@map h   // ios: Data/Sync.swift:444
            sub[h.symbol] = s.last   // ios: Data/Sync.swift:445
            Holding(symbol = h.symbol, qty = h.qty, avg = s.last)   // ios: Data/Sync.swift:446
        })
        keepTickers = keep; subAvg = sub   // ios: Data/Sync.swift:448
        val app = jObj(st["app"])   // ios: Data/Sync.swift:449
        if (!m.onboarded && app?.get("progress") == null) {   // ios: Data/Sync.swift:450
            // 사이트에서만 쓰던 값: 미션은 처음부터. 이익이면 목표 루트, 손실이면 회복 루트
            val cost = hs.fold(0.0) { a, h -> a + (Sample.symbol(h.symbol)?.let { m.krw(it, h.qty * h.avg) } ?: 0.0) }   // ios: Data/Sync.swift:452
            val v = hs.fold(0.0) { a, h -> a + (Sample.symbol(h.symbol)?.let { m.krw(it, h.qty * it.last) } ?: 0.0) }   // ios: Data/Sync.swift:453
            // 웹 홈에서 길을 이미 골랐으면 그 길로
            val web = if ((st["sample"] as? Boolean) == true) null else st["route"] as? String   // ios: Data/Sync.swift:455
            m.startRoute(if (web == "recover") Route.recover else if (web == "goal") Route.plus else if (cost > 0 && v >= cost) Route.plus else Route.recover)   // ios: Data/Sync.swift:456
        }
        if (app != null) {   // ios: Data/Sync.swift:458
            for (x in jObjs(app["virtual"]) ?: emptyList()) {   // ios: Data/Sync.swift:459
                val t = x["t"] as? String; val q = x["q"] as? Double; val a = x["a"] as? Double; if (t != null && q != null && a != null) hs.add(0, Holding(symbol = t, qty = q, avg = a))   // ios: Data/Sync.swift:460
            }
            townIn(app["town"])   // ios: Data/Sync.swift:462
            val p = app["parallel"]; val w = p?.let { runCatching { NJson.decodeFromJsonElement(PWorld.serializer(), plainToJson(it)) }.getOrNull() }   // ios: Data/Sync.swift:463,464
            if (w != null) {
                m.pworld = PWorld.merge(m.pworld, w)   // ios: Data/Sync.swift:465
            }
            val pp = app["progress"]; val prog = pp?.let { runCatching { NJson.decodeFromJsonElement(Progress.serializer(), plainToJson(it)) }.getOrNull() }   // ios: Data/Sync.swift:467,468
            if (prog != null) {
                m.apply(prog)   // ios: Data/Sync.swift:469
            }
        }
        // 웹 홈에서 고른 길(route)을 따른다 (진행보다 뒤에: 웹에서 바꾼 길이 진행에 든 예전 길을 덮는다). 목표는 플러스·처음 중 지금 것을 그대로
        val r = st["route"] as? String   // ios: Data/Sync.swift:473
        if ((st["sample"] as? Boolean) != true && r != null) {
            if (r == "recover" && m.isGoal) m.switchRoute(Route.recover)   // ios: Data/Sync.swift:474
            else if (r == "goal" && !m.isGoal) m.switchRoute(Route.plus)   // ios: Data/Sync.swift:475
        }
        // 목표(금액·목표일·시작일·매달 넣는 돈)는 받을 때마다 사이트 값으로 맞춘다 (진행보다 뒤에: 진행에 든 예전 목표를 덮는다).
        // 회복 루트는 본전이 기준이라 화면에는 안 쓰지만, 목표 루트로 넘어갈 때 이 목표를 그대로 쓴다. 사이트 샘플 목표는 받지 않는다
        val g = jObj(st["goal"]); if ((st["sample"] as? Boolean) != true && g != null) m.takeSiteGoal(g)   // ios: Data/Sync.swift:479
        // 전망 관점·추세 신뢰도 웹 설정을 그대로 (진행보다 뒤에: 앱에 따로 남은 값이 웹과 다른 연 기대·확률을 만들지 않게)
        val md = jObj(st["model"])   // ios: Data/Sync.swift:481
        if (md != null) {
            when (md["scenario"] as? String) { "base" -> m.lens = Lens.base; "smooth" -> m.lens = Lens.smooth; "blend" -> m.lens = Lens.mine; else -> {} }   // ios: Data/Sync.swift:482
            if (md["trust"] != null) { val t = num(md["trust"]); if (t.isFinite()) m.trust = minOf(100.0, maxOf(0.0, t)) }   // ios: Data/Sync.swift:483
        }
        m.myRecord = MyRecord.merge(m.myRecord, MyRecord.fromJSON(st["daily"]?.let(::plainToJson)))   // ios: Data/Sync.swift:485
        m.lots = Lots.withSite(Lots.fromJSON(st["lots"]?.let(::plainToJson)), m.lots)    // 종목보다 먼저: 종목을 바꾸면 수량 기록이 오늘 줄을 남긴다   // ios: Data/Sync.swift:486
        m.holdings = hs   // ios: Data/Sync.swift:487
        // 새 폰에서 첫 질문 전에 불러왔으면 첫 질문은 건너뛴다 (진행이 없으면 회복 루트 미션 1부터)
        if (!m.onboarded) { m.onboarded = true; Store.defaults.edit().putBoolean("onboarded", true).apply(); m.tab = Tab.board }   // ios: Data/Sync.swift:489
        siteState = st   // ios: Data/Sync.swift:490
        localAt = at   // ios: Data/Sync.swift:491
        lastSig = signature(m)   // ios: Data/Sync.swift:492
        if (skipped.isNotEmpty()) state = State.failed((if (com.naeilo.app.ui.town.TownLook.batchim(skipped.lastOrNull() ?: "")) "시세를 못 받은 종목 ${skipped.joinToString(", ")}은 사이트에만 있어요" else "시세를 못 받은 종목 ${skipped.joinToString(", ")}는 사이트에만 있어요"))   // Data/Sync.swift:470 (검사 예외: 옮김)
    }

    /** 동기화할 값이 바뀌었는지 (종목 + 진행 + 내 기록 날짜 수. 날짜 칸은 빼고). 이 기기 안에서만 비교한다 */
    // MARK: 동네 (app.town)
    /** 앱 설정 키 → 동기화 칸 이름. 수첩·기록은 앱 설정에 Base64 JSON(백업과 같은 꼴), 동기화에는 JSON 그대로 */
    private val townData = listOf("town.book" to "book", "town.log" to "log")   // ios: Data/Sync.swift:497

    /** 이 기기 동네 값. 하나도 없으면 받은 값(old)을 그대로 다시 올린다 (새 폰이 빈 동네로 덮지 않게) */
    fun townOut(old: Any?): Any? {   // ios: Data/Sync.swift:500
        val d = Defaults.prefs   // ios: Data/Sync.swift:501
        val t = mutableMapOf<String, Any?>()   // ios: Data/Sync.swift:502
        for ((k, n) in townData) {   // ios: Data/Sync.swift:503
            val j = d.getString(k, null)?.let { runCatching { jsonToPlain(NJson.parseToJsonElement(Base64.getDecoder().decode(it).decodeToString())) }.getOrNull() }
            if (j != null) t[n] = j
        }
        val bg = d.getInt("town.bg", 0); val owned = d.getString("town.owned", null) ?: ""; val candy = d.getInt("town.candy", 0)   // ios: Data/Sync.swift:504
        if (t.isEmpty() && bg == 0 && owned.isEmpty() && candy == 0) return old   // ios: Data/Sync.swift:505
        t["bg"] = bg; t["owned"] = owned; t["candy"] = candy   // ios: Data/Sync.swift:506
        return t   // ios: Data/Sync.swift:507
    }
    /** 받은 동네 값을 이 기기에 (나중에 고친 쪽이 이긴다) */
    fun townIn(v: Any?) {   // ios: Data/Sync.swift:510
        val t = v as? Map<*, *> ?: return   // ios: Data/Sync.swift:511
        val e = Defaults.prefs.edit()   // ios: Data/Sync.swift:512
        for ((k, n) in townData) { val j = t[n] ?: continue; e.putString(k, Base64.getEncoder().encodeToString(plainToJson(j).toString().encodeToByteArray())) }   // ios: Data/Sync.swift:513
        if (t["owned"] is String) e.putString("town.owned", t["owned"] as String)   // ios: Data/Sync.swift:515
        if (t["candy"] != null) e.putInt("town.candy", num(t["candy"]).toInt())   // ios: Data/Sync.swift:516
        e.apply()
        // 배경은 화면들이 같이 쓰는 상태로 (꾸미기 화면이 바로 새 값을 보게)
        if (t["bg"] != null) townPrefInt("town.bg", 0).value = num(t["bg"]).toInt()   // ios: Data/Sync.swift:514
        TownBook.shared.reload(); TownLog.shared.reload()   // ios: Data/Sync.swift:517
    }
    /** 바뀜 감지용 동네 값 (수첩·기록 원문 + 배경·물건·별사탕) */
    private fun townStamp(): ByteArray {   // ios: Data/Sync.swift:520
        val d = Defaults.prefs   // ios: Data/Sync.swift:521
        return townData.fold(ByteArray(0)) { a, (k, _) -> a + (d.getString(k, null) ?: "").encodeToByteArray() } +   // ios: Data/Sync.swift:522
            "|${d.getInt("town.bg", 0)}|${d.getString("town.owned", null) ?: ""}|${d.getInt("town.candy", 0)}".encodeToByteArray()   // Data/Sync.swift:506 (검사 예외: 옮김)
    }

    fun signature(m: AppModel): String {   // ios: Data/Sync.swift:527
        val p = m.progress.copy(lastOpen = "")   // ios: Data/Sync.swift:528
        val a = runCatching { NJson.encodeToString(Progress.serializer(), p).encodeToByteArray() }.getOrNull() ?: ByteArray(0); val b = runCatching { NJson.encodeToString(ListSerializer(Holding.serializer()), m.holdings).encodeToByteArray() }.getOrNull() ?: ByteArray(0)   // ios: Data/Sync.swift:529
        val pw = m.pworld?.let { "${it.days.size}|${it.segs.size}|${it.segs.lastOrNull()?.from ?: ""}|${it.who}|${it.opened}" } ?: "-"   // Data/Sync.swift:477 (검사 예외: 옮김)
        val cm = "${m.calm.events.size}|${m.calm.events.lastOrNull()?.day ?: ""}|${m.calm.type ?: ""}|${m.calm.together.size}"   // Data/Sync.swift:523 (검사 예외: 옮김)
        val c = "${m.myRecord.size}|${m.myRecord.lastOrNull()?.d ?: ""}|${CustomSymbols.shared.krStamp}|${pw}|${cm}".encodeToByteArray() + townStamp()   // 내 기록은 새 날이 생길 때만 보낸다 (같은 날 시세 변화로는 안 보냄)   // Data/Sync.swift:478 (검사 예외: 옮김)
        return MessageDigest.getInstance("SHA-256").digest(a + b + c).joinToString("") { "%02x".format(it.toInt() and 0xFF) }   // ios: Data/Sync.swift:533
    }
}

/** 사이트 입력값의 사건 목록·전망 설정 (Swift 튜플 (events, model)) */
data class SiteForecast(val events: List<MC.Event>?, val model: MC.Settings?)

/** JSON 값 하나를 같은지 비교하며 들고 다니기 위한 상자 (Kotlin Map·List·Double 은 원래 값으로 비교된다) */
data class AnyCodable(val value: Any?)   // ios: Data/Sync.swift:538,539,540,541,542

/** 동기화 키는 키체인 대신 KeyVault 에 (Android Keystore 로 감싼다: 이 기기에서만, 백업으로 옮겨져도 못 푼다) */
object Keychain {   // ios: Data/Sync.swift:547
    private val q = KeyVault.slot(service = "com.naeilo.sync", account = "esync")   // ios: Data/Sync.swift:548
    fun save(c: Sync.Creds) {   // ios: Data/Sync.swift:549
        delete()   // ios: Data/Sync.swift:550
        val a = runCatching { NJson.encodeToString(Sync.Creds.serializer(), c).encodeToByteArray() }.getOrNull() ?: return   // ios: Data/Sync.swift:551,552
        KeyVault.add(q, a)   // ios: Data/Sync.swift:554
    }
    fun load(): Sync.Creds? {   // ios: Data/Sync.swift:556
        val d = KeyVault.read(q) ?: return null   // ios: Data/Sync.swift:557,558,559,560,561
        return runCatching { NJson.decodeFromString(Sync.Creds.serializer(), d.decodeToString()) }.getOrNull()   // ios: Data/Sync.swift:562
    }
    fun delete() { KeyVault.delete(q) }   // ios: Data/Sync.swift:564
}
