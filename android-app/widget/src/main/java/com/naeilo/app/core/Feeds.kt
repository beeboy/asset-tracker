package com.naeilo.app.core

import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

// 토스증권 지금 가격. 두 길이 있다 (개발자 주소가 있으면 그쪽이 먼저):
// - 개발자: 키를 넣은 PC 의 server.py 를 Tailscale 주소로 부른다.
//   주소는 naeilo://toss?u=https://내PC.ts.net 을 한 번 열면 저장 (u 가 비면 지움). 저장소에 주소를 쓰지 않는다.
// - 일반 사용자: 자기 토스 키(TossKeys, 이 기기 Keystore)로 공용 중계(Config.tossRelay)를 거쳐 토스를 직접 부른다.
//   중계는 키·토큰을 저장하지 않고 넘기기만 한다. 사용자는 중계 IP 를 토스 허용 IP 로 등록해야 한다.
// 국내·미국 종목만 오고, 3초를 기다린다. 실패하면 15분 안에 받은 값은 그대로 쓰고, 그보다 오래되면 Yahoo 값을 쓴다.
object TossFeed {   // ios: WidgetShared/Models.swift:154
    const val name = "toss_quotes.json"   // ios: WidgetShared/Models.swift:155
    const val key = "toss_base"   // ios: WidgetShared/Models.swift:156
    var base: String?   // ios: WidgetShared/Models.swift:157
        get() = if (TossKeys.paused) null else Store.defaults.getString(key, null)?.let { if (it.isEmpty()) null else it }   // 토스 멈춤 중엔 개발자 주소도 안 쓴다   // ios: WidgetShared/Models.swift:159
        set(newValue) { if (newValue == null) Store.defaults.edit().remove(key).apply() else Store.defaults.edit().putString(key, newValue).apply() }   // ios: WidgetShared/Models.swift:159
    /** 설정 > 개발자용에 보이는 상태: 마지막 성공 시각(초), 마지막 실패 이유 (성공하면 지움) */
    val okAt: Double get() = Store.defaults.getDouble("toss_ok_at")   // ios: WidgetShared/Models.swift:162
    val lastError: String? get() = Store.defaults.getString("toss_err", null)   // ios: WidgetShared/Models.swift:163
    private fun fail(why: String): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:164
        Store.defaults.edit().putString("toss_err", why).apply()   // ios: WidgetShared/Models.swift:165
        return cached()   // ios: WidgetShared/Models.swift:166
    }
    /** naeilo://toss?u=… 로 주소를 넣거나 지운다. ts.net 주소만 받는다 */
    fun setBase(from: Uri) {   // ios: WidgetShared/Models.swift:169
        val u = runCatching { from.getQueryParameter("u") }.getOrNull() ?: ""   // ios: WidgetShared/Models.swift:170
        val h = runCatching { URI(u).host }.getOrNull(); if (h != null && u.startsWith("https://") && h.endsWith(".ts.net")) {   // ios: WidgetShared/Models.swift:171
            base = if (u.endsWith("/")) u.dropLast(1) else u   // ios: WidgetShared/Models.swift:172
        } else { base = null; Store.remove(name) }   // ios: WidgetShared/Models.swift:173
    }
    /** 15분 안에 받은 값만 (오래된 토스 값보다 Yahoo 값이 낫다) */
    fun cached(): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:176
        val f = (if (base != null || TossKeys.on) Store.read(QuoteFeed.File.serializer(), name) else null)?.takeIf { nowSec() - it.got < 900 } ?: return null   // ios: WidgetShared/Models.swift:177
        return f   // ios: WidgetShared/Models.swift:178
    }

    /** syms: 종목 → Yahoo 기호. prev 는 모르므로 0 (전일 종가는 사이트·Yahoo 값을 쓴다)
     *  force: 다시 받기·당겨 갱신. 하루 빼 둔 종목도 다시 묻는다 */
    suspend fun fetch(syms: Map<String, String>, minAge: Double = 0.0, force: Boolean = false): QuoteFeed.File? = withContext(Dispatchers.IO) { fetchIO(syms, minAge, force) }   // ios: WidgetShared/Models.swift:183,184
    private suspend fun fetchIO(syms: Map<String, String>, minAge: Double, force: Boolean): QuoteFeed.File? {
        val want = syms.filter { !it.value.contains("^") && !it.value.contains("=") }   // ios: WidgetShared/Models.swift:185
        if (want.isEmpty()) return null   // ios: WidgetShared/Models.swift:186
        cached()?.let { o -> if (nowSec() - o.got < minAge) return o }   // ios: WidgetShared/Models.swift:187
        val b = base ?: return if (TossKeys.on) fetchUser(want, force = force) else null   // ios: WidgetShared/Models.swift:188
        val list = want.values.toSet().sorted().joinToString(",")   // ios: WidgetShared/Models.swift:189
        val enc = pctEncode(list)   // ios: WidgetShared/Models.swift:190
        val url = uriOrNull(b + "/api/toss/prices?symbols=" + enc) ?: return null   // ios: WidgetShared/Models.swift:191
        // 3초 넘으면 포기 (웹 tossQuotes 와 같다). 없는 종목은 server.py 가 하루 제외하므로 다음 번엔 빨라진다
        val req = FeedReq(url, timeout = 3.0)   // ios: WidgetShared/Models.swift:193
        req.setValue("naeilo-ios/0.1", "User-Agent")   // ios: WidgetShared/Models.swift:194
        val res: Pair<Int, ByteArray> = try { req.send() }   // ios: WidgetShared/Models.swift:195,196
        catch (e: IOException) { return fail(if (e is SocketTimeoutException) "3초 안에 응답 없음" else "연결 실패 (${e.javaClass.simpleName})") }   // ios: WidgetShared/Models.swift:197
        catch (e: Exception) { return fail("연결 실패") }   // ios: WidgetShared/Models.swift:198
        val code = res.first.coerceAtLeast(0)   // ios: WidgetShared/Models.swift:199
        if (code != 200) return fail("서버 응답 $code")   // ios: WidgetShared/Models.swift:200
        val o = jObj(feedJson(res.second))   // ios: WidgetShared/Models.swift:201,202
        val qs = jObj(o?.get("quotes")); if (o == null || qs == null) return fail("응답을 읽지 못함")
        val q = mutableMapOf<String, QuoteFeed.Q>()   // ios: WidgetShared/Models.swift:203
        for ((id, y) in want) {   // ios: WidgetShared/Models.swift:204
            val v = jObj(qs[y]) ?: continue; val last = num(v["last"])?.takeIf { it > 0 } ?: continue   // ios: WidgetShared/Models.swift:205
            q[id] = QuoteFeed.Q(last = last, prev = 0.0, at = num(v["last_time"]) ?: nowSec())   // ios: WidgetShared/Models.swift:206
        }
        if (q.isEmpty()) return fail((o["error"] as? String)?.let { "서버: " + it } ?: "받은 종목 없음")   // ios: WidgetShared/Models.swift:208
        return save(q)   // ios: WidgetShared/Models.swift:209
    }

    private fun save(q: Map<String, QuoteFeed.Q>): QuoteFeed.File {   // ios: WidgetShared/Models.swift:212
        val f = QuoteFeed.File(got = nowSec(), q = q)   // ios: WidgetShared/Models.swift:213
        Store.write(QuoteFeed.File.serializer(), f, name)   // ios: WidgetShared/Models.swift:214
        Store.defaults.edit().putDouble("toss_ok_at", f.got)   // ios: WidgetShared/Models.swift:215
            .remove("toss_err").apply()   // ios: WidgetShared/Models.swift:216
        return f   // ios: WidgetShared/Models.swift:217
    }

    /** Yahoo 기호 → 토스 종목 코드 (server.py toss_code 와 같다). 토스에 없는 시장은 null
     *  국내 005930.KS·0101N0.KQ → 6자리, 미국 AAPL → AAPL, BRK-B → BRK.B */
    fun code(sym: String): String? {   // ios: WidgetShared/Models.swift:222
        val u = sym.trim().uppercase()   // ios: WidgetShared/Models.swift:223
        if (u.endsWith(".KS") || u.endsWith(".KQ")) {   // ios: WidgetShared/Models.swift:224
            val c = u.dropLast(3)   // ios: WidgetShared/Models.swift:225
            return if (c.length == 6 && c.all { it.code < 128 && it.isLetterOrDigit() }) c else null   // ios: WidgetShared/Models.swift:226
        }
        // ^GSPC·KRW=X·GC=F·BTC-USD·7203.T 는 여기서 걸러진다
        val p = u.split("-")   // ios: WidgetShared/Models.swift:229
        val az = { x: String -> x.all { "A" <= it.toString() && it.toString() <= "Z" } }   // ios: WidgetShared/Models.swift:230
        if (p.size !in 1..2 || p[0].length !in 1..5 || !az(p[0])) return null   // ios: WidgetShared/Models.swift:231
        if (p.size == 1) return p[0]   // ios: WidgetShared/Models.swift:232
        return if (p[1].length == 1 && az(p[1])) p[0] + "." + p[1] else null   // ios: WidgetShared/Models.swift:233
    }

    // MARK: 일반 사용자 (내 키 + 공용 중계)

    private suspend fun relayCall(r: FeedReq): Triple<Int, Map<String, Any?>?, String?> {   // ios: WidgetShared/Models.swift:238
        try {   // ios: WidgetShared/Models.swift:239
            val (code, d) = r.send()   // ios: WidgetShared/Models.swift:240
            return Triple(code.coerceAtLeast(0), jObj(feedJson(d)), null)   // ios: WidgetShared/Models.swift:241
        } catch (e: IOException) { return Triple(0, null, if (e is SocketTimeoutException) "3초 안에 응답 없음" else "중계에 닿지 못함") }   // ios: WidgetShared/Models.swift:242
        catch (e: Exception) { return Triple(0, null, "중계에 닿지 못함") }   // ios: WidgetShared/Models.swift:243
    }

    /** 토스 오류를 쉬운 말로 (허용 IP 미등록이 가장 흔하다) */
    fun why(code: Int, o: Map<String, Any?>?): String {   // ios: WidgetShared/Models.swift:247
        val c = (jObj(o?.get("error"))?.get("code") as? String) ?: (o?.get("error") as? String) ?: ""   // ios: WidgetShared/Models.swift:248
        if (code == 403 || c == "access_denied") return "허용 IP에 중계 IP가 없어요"   // ios: WidgetShared/Models.swift:249
        if (c == "invalid_client") return "키가 맞지 않아요"   // ios: WidgetShared/Models.swift:250
        if (code == 429) return "잠시 뒤에 다시 받아요"   // ios: WidgetShared/Models.swift:251
        return "오픈 API 응답 $code" + (if (c.isEmpty()) "" else " ($c)")   // ios: WidgetShared/Models.swift:252
    }

    /** 토큰: Keystore 로 감싸 둔 것을 만료 1분 전까지 쓴다. 토스는 키당 토큰 1개라 새로 받으면 예전 것은 끊긴다 */
    suspend fun token(renew: Boolean = false): Pair<String?, String?> {   // ios: WidgetShared/Models.swift:256
        if (!renew) TossKeys.token?.let { t -> if (t.exp - 60 > nowSec()) return t.value to null }   // ios: WidgetShared/Models.swift:257
        val k = TossKeys.load(); val url = uriOrNull(Config.tossRelay + "/api/toss/token"); if (k == null || url == null) return null to "키 없음"   // ios: WidgetShared/Models.swift:258
        val r = FeedReq(url, timeout = 5.0)   // ios: WidgetShared/Models.swift:259
        r.httpMethod = "POST"   // ios: WidgetShared/Models.swift:260
        r.setValue("application/x-www-form-urlencoded", "Content-Type")   // ios: WidgetShared/Models.swift:261
        val ok = "-._~"   // ios: WidgetShared/Models.swift:262
        val enc = { v: String -> pctEncode(v, ok) }   // ios: WidgetShared/Models.swift:263
        r.httpBody = "client_id=${enc(k.id)}&client_secret=${enc(k.secret)}".toByteArray(Charsets.UTF_8)   // ios: WidgetShared/Models.swift:264
        val (code, o, e) = relayCall(r)   // ios: WidgetShared/Models.swift:265
        if (e != null) return null to e   // ios: WidgetShared/Models.swift:266
        val t = o?.get("access_token") as? String; if (code != 200 || o == null || t == null) return null to why(code, o)   // ios: WidgetShared/Models.swift:267
        TossKeys.token = TossKeys.Token(value = t, exp = nowSec() + (num(o["expires_in"]) ?: 3600.0))   // ios: WidgetShared/Models.swift:268
        return t to null   // ios: WidgetShared/Models.swift:269
    }

    private suspend fun prices(codes: List<String>, tok: String): Triple<Int, Map<String, Any?>?, String?> {   // ios: WidgetShared/Models.swift:272
        val url = uriOrNull(Config.tossRelay + "/api/toss/v1/prices?symbols=" + codes.joinToString(",")) ?: return Triple(0, null, "잘못된 종목")   // ios: WidgetShared/Models.swift:273
        val r = FeedReq(url, timeout = 3.0)   // ios: WidgetShared/Models.swift:274
        r.setValue("Bearer " + tok, "Authorization")   // ios: WidgetShared/Models.swift:275
        return relayCall(r)   // ios: WidgetShared/Models.swift:276
    }

    /** 내 키로 중계 GET (일봉·환율). 토큰이 끊겼으면(401) 한 번만 새로 받는다. path 예: "/api/toss/v1/candles?symbol=AAPL&…" */
    suspend fun userGet(path: String, timeout: Double = 5.0): Triple<Int, Map<String, Any?>?, String?> {   // ios: WidgetShared/Models.swift:280
        val url = uriOrNull(Config.tossRelay + path); if (!TossKeys.on || url == null) return Triple(0, null, "키 없음")   // ios: WidgetShared/Models.swift:281
        var (tok, e) = token()   // ios: WidgetShared/Models.swift:282
        for (renew in listOf(false, true)) {   // ios: WidgetShared/Models.swift:283
            if (renew) token(renew = true).also { tok = it.first; e = it.second }   // ios: WidgetShared/Models.swift:284
            val t = tok ?: return Triple(0, null, e ?: "토큰 실패")   // ios: WidgetShared/Models.swift:285
            val r = FeedReq(url, timeout)   // ios: WidgetShared/Models.swift:286
            r.setValue("Bearer " + t, "Authorization")   // ios: WidgetShared/Models.swift:287
            val out = relayCall(r)   // ios: WidgetShared/Models.swift:288
            if (out.first != 401) return out   // ios: WidgetShared/Models.swift:289
        }
        return Triple(401, null, "토큰 실패")   // ios: WidgetShared/Models.swift:291
    }

    /** 토스에 없는 종목 코드 → 다시 물어볼 시각 (하루 동안 안 묻는다, server.py 와 같다). 현재가에서 404 난 것만 */
    var miss: Map<String, Double>   // ios: WidgetShared/Models.swift:295
        get() = Store.defaults.dblMap("toss_miss")   // ios: WidgetShared/Models.swift:296
        set(newValue) { Store.defaults.edit().putDblMap("toss_miss", newValue).apply() }   // ios: WidgetShared/Models.swift:297
    /** 일봉에서 404 난 종목 (현재가 목록과 따로 둔다. 같이 쓰면 일봉 404 하나로 그 종목 현재가도 하루 못 받는다) */
    var histMiss: Map<String, Double>   // ios: WidgetShared/Models.swift:300
        get() = Store.defaults.dblMap("toss_hist_miss")   // ios: WidgetShared/Models.swift:301
        set(newValue) { Store.defaults.edit().putDblMap("toss_hist_miss", newValue).apply() }   // ios: WidgetShared/Models.swift:302

    /** 위젯: 앱이 feed.json 에 넘긴 내 종목을 위젯이 직접 토스에 묻는다 (앱을 안 열어도 위젯이 토스 값) */
    suspend fun widgetFetch(minAge: Double): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:306,307
        val ty = (if (base != null || TossKeys.on) Store.read(WFeed.serializer(), "feed.json")?.ty else null)?.takeIf { it.isNotEmpty() } ?: return null   // ios: WidgetShared/Models.swift:308
        return fetch(ty, minAge = minAge)   // ios: WidgetShared/Models.swift:309
    }

    private suspend fun fetchUser(want: Map<String, String>, force: Boolean): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:312
        val now = nowSec()   // ios: WidgetShared/Models.swift:313
        val ids = mutableMapOf<String, MutableList<String>>()   // 토스 코드 → 앱 종목들   // ios: WidgetShared/Models.swift:314
        var skip: Map<String, Double> = if (force) emptyMap() else miss     // 다시 받기는 빼 둔 종목도 다시 묻는다 (없으면 아래에서 다시 뺀다)   // ios: WidgetShared/Models.swift:315
        // 내 종목이 모두 빠져 있으면 그래도 묻는다 (한 번 404 가 나면 다시 받기 전까지 하루 내내 실패로 남던 일)
        if (skip.isNotEmpty() && want.values.mapNotNull { code(it) }.all { (skip[it] ?: 0.0) >= now }) skip = emptyMap()   // ios: WidgetShared/Models.swift:317
        for ((id, y) in want) { val c = code(y); if (c != null && (skip[c] ?: 0.0) < now) ids.getOrPut(c) { mutableListOf() }.add(id) }   // ios: WidgetShared/Models.swift:318
        if (ids.isEmpty()) return fail("오픈 API로 받을 종목 없음")   // ios: WidgetShared/Models.swift:319
        var (tok, e) = token()   // ios: WidgetShared/Models.swift:320
        var t = tok ?: return fail(e ?: "토큰 실패")   // ios: WidgetShared/Models.swift:321
        val q = mutableMapOf<String, QuoteFeed.Q>()   // ios: WidgetShared/Models.swift:322
        val got = mutableSetOf<String>()   // 받은 토스 코드 (빼 둔 목록에서 지운다)   // ios: WidgetShared/Models.swift:323
        fun take(o: Map<String, Any?>?) {   // ios: WidgetShared/Models.swift:324
            for (v in jObjs(o?.get("result")) ?: emptyList()) {   // ios: WidgetShared/Models.swift:325
                val c = (v["symbol"] as? String)?.uppercase() ?: continue; val last = num(v["lastPrice"])?.takeIf { it > 0 } ?: continue   // ios: WidgetShared/Models.swift:326
                got.add(c)   // ios: WidgetShared/Models.swift:327
                val at = (v["timestamp"] as? String)?.let { isoSec(it) } ?: now   // ios: WidgetShared/Models.swift:328
                for (id in ids[c] ?: emptyList()) q[id] = QuoteFeed.Q(last = last, prev = 0.0, at = at)   // ios: WidgetShared/Models.swift:329
            }
        }
        val all = ids.keys.sorted()   // ios: WidgetShared/Models.swift:332
        for (i in 0 until all.size step 200) {   // 한 번에 200개까지   // ios: WidgetShared/Models.swift:333
            val part = all.subList(i, min(i + 200, all.size))   // ios: WidgetShared/Models.swift:334
            var (code, o, err) = prices(part, t)   // ios: WidgetShared/Models.swift:335
            if (code == 401) {   // 만료·다른 곳에서 새로 받아 끊김 → 한 번만 다시 받는다   // ios: WidgetShared/Models.swift:336
                token(renew = true).also { tok = it.first; e = it.second }   // ios: WidgetShared/Models.swift:337
                val n = tok ?: return fail(e ?: "토큰 실패")   // ios: WidgetShared/Models.swift:338
                t = n   // ios: WidgetShared/Models.swift:339
                prices(part, t).also { code = it.first; o = it.second; err = it.third }   // ios: WidgetShared/Models.swift:340
            }
            err?.let { return fail(it) }   // ios: WidgetShared/Models.swift:342
            if (code == 404 && part.size > 1) {   // ios: WidgetShared/Models.swift:343
                // 묶음 안에 토스에 없는 종목이 있으면 하나씩 동시에 묻고, 없는 것은 하루 뺀다
                val m = miss.toMutableMap()   // ios: WidgetShared/Models.swift:345
                val tk = t   // ios: WidgetShared/Models.swift:346
                coroutineScope {   // ios: WidgetShared/Models.swift:347
                    val g = part.take(15).map { c -> async { val (k, oo, _) = prices(listOf(c), tk); Triple(c, k, oo) } }   // ios: WidgetShared/Models.swift:348
                    for ((c, k, oo) in g.awaitAll()) { if (k == 404) m[c] = now + 86400 else if (k == 200) take(oo) }   // ios: WidgetShared/Models.swift:349
                }
                miss = m   // ios: WidgetShared/Models.swift:351
                continue   // ios: WidgetShared/Models.swift:352
            }
            if (code == 404) { val m = miss.toMutableMap(); m[part[0]] = now + 86400; miss = m; continue }   // ios: WidgetShared/Models.swift:354
            if (code != 200) return fail(why(code, o))   // ios: WidgetShared/Models.swift:355
            take(o)   // ios: WidgetShared/Models.swift:356
        }
        if (got.isNotEmpty() && miss.keys.any { it in got }) { val m = miss.toMutableMap(); for (c in got) m.remove(c); miss = m }   // ios: WidgetShared/Models.swift:358
        if (q.isEmpty()) return fail("받은 종목 없음")   // ios: WidgetShared/Models.swift:359
        return save(q)   // ios: WidgetShared/Models.swift:360
    }
}

/** 사용자가 넣은 토스증권 키와 토큰. 이 기기 Keystore 로 감싸 둔다 (백업·동기화·서버에 안 들어감) */
object TossKeys {   // ios: WidgetShared/Models.swift:365
    @Serializable data class Keys(var id: String, var secret: String)   // ios: WidgetShared/Models.swift:366
    @Serializable data class Token(var value: String, var exp: Double)   // ios: WidgetShared/Models.swift:367
    private fun q(account: String): String =   // ios: WidgetShared/Models.swift:368
        KeyVault.slot("com.naeilo.toss", account)   // ios: WidgetShared/Models.swift:369
    /** 아이폰은 앱 그룹을 키체인 그룹으로 써서 위젯과 같이 읽는다. 안드로이드는 위젯이 같은 앱이라 따로 할 일이 없다 */
    const val group = "group.com.naeilo.widget"   // ios: WidgetShared/Models.swift:372
    private fun <T> put(v: T?, ser: KSerializer<T>, account: String) {   // ios: WidgetShared/Models.swift:373
        KeyVault.delete(q(account))   // ios: WidgetShared/Models.swift:374
        if (v == null) return; val d = runCatching { NJson.encodeToString(ser, v).toByteArray(Charsets.UTF_8) }.getOrNull() ?: return   // ios: WidgetShared/Models.swift:375
        val a = q(account)   // ios: WidgetShared/Models.swift:376
        KeyVault.add(a, d)   // ios: WidgetShared/Models.swift:378,380
    }
    private fun <T> get(t: KSerializer<T>, account: String): T? {   // ios: WidgetShared/Models.swift:382
        val a = q(account)   // ios: WidgetShared/Models.swift:383
        val d = KeyVault.read(a) ?: return null   // ios: WidgetShared/Models.swift:384,385,386,387
        return runCatching { NJson.decodeFromString(t, d.decodeToString()) }.getOrNull()   // ios: WidgetShared/Models.swift:388
    }
    /** 키를 넣었는지 (위젯도 보므로 키 자체가 아니라 표시만 공유 저장소에 둔다) */
    val stored: Boolean get() = Store.defaults.getBoolean("toss_user", false)   // ios: WidgetShared/Models.swift:395
    /** 개발자용 잠시 멈춤 (내부 베타만): 키는 그대로 두고 없는 것처럼 안 부른다 */
    val paused: Boolean get() = Config.isInternal && Store.defaults.getBoolean("toss_pause", false)   // ios: WidgetShared/Models.swift:397
    val on: Boolean get() = stored && !paused   // ios: WidgetShared/Models.swift:398
    fun load(): Keys? = get(Keys.serializer(), "keys")   // ios: WidgetShared/Models.swift:392
    /** 아이폰 예전 판은 키를 앱 전용 자리에 두어 위젯이 못 읽었다. 앱이 열릴 때 한 번 그룹 자리로 옮긴다 (안드로이드는 같은 자리에 다시 쓴다) */
    fun share() {   // ios: WidgetShared/Models.swift:394
        if (!stored || Store.defaults.getBoolean("toss_kc_group", false)) return   // ios: WidgetShared/Models.swift:395
        load()?.let { k -> val t = token; put(k, Keys.serializer(), "keys"); put(t, Token.serializer(), "token") }   // ios: WidgetShared/Models.swift:399
        Store.defaults.edit().putBoolean("toss_kc_group", true).apply()   // ios: WidgetShared/Models.swift:400
    }
    fun save(k: Keys) {   // ios: WidgetShared/Models.swift:399
        put(k, Keys.serializer(), "keys"); put(null, Token.serializer(), "token")   // ios: WidgetShared/Models.swift:403
        Store.defaults.edit().putBoolean("toss_user", true)   // ios: WidgetShared/Models.swift:404
            .remove("toss_miss")   // ios: WidgetShared/Models.swift:405
            .remove("toss_hist_miss").apply()   // ios: WidgetShared/Models.swift:406
    }
    fun clear() {   // ios: WidgetShared/Models.swift:408
        put(null, Keys.serializer(), "keys"); put(null, Token.serializer(), "token")   // ios: WidgetShared/Models.swift:409
        Store.defaults.edit().putBoolean("toss_user", false)   // ios: WidgetShared/Models.swift:410
            .remove("toss_err")   // ios: WidgetShared/Models.swift:411
            .remove("toss_ok_at").apply()   // ios: WidgetShared/Models.swift:412
        Store.remove(TossFeed.name)   // ios: WidgetShared/Models.swift:413
        Store.remove("toss_history.json")   // 내 키로 받은 지난 종가·환율 (앱 TossHistory)   // ios: WidgetShared/Models.swift:414
    }
    var token: Token?   // ios: WidgetShared/Models.swift:416
        get() = get(Token.serializer(), "token")   // ios: WidgetShared/Models.swift:417
        set(newValue) { put(newValue, Token.serializer(), "token") }   // ios: WidgetShared/Models.swift:418
    /** 사용자가 토스에 등록할 중계 IP (중계가 알려 준다) */
    suspend fun relayIP(): String? {   // ios: WidgetShared/Models.swift:421
        val url = uriOrNull(Config.tossRelay + "/api/toss/ip") ?: return null   // ios: WidgetShared/Models.swift:422
        val (_, d) = runCatching { FeedReq(url, timeout = 5.0).send() }.getOrNull() ?: return null   // ios: WidgetShared/Models.swift:423
        val ip = jObj(feedJson(d))?.get("ip") as? String; if (ip.isNullOrEmpty()) return null   // ios: WidgetShared/Models.swift:424
        return ip   // ios: WidgetShared/Models.swift:425
    }
}

// Alpaca 지금 가격 (미국 종목만). 사용자가 app.alpaca.markets 모의 계정에서 무료로 받은 키로 Alpaca 를 직접 부른다 (중계 없음).
// 무료 키는 IEX 거래소 실시간. 우선순위: 토스 키 > Alpaca 키 > 사이트 시세. 한국 종목·원/달러는 그대로.
// 전일 종가는 사이트(공식 종가) 값을 먼저 쓰고, 없을 때만 Alpaca prevDailyBar(IEX 종가)를 쓴다.
object AlpacaFeed {   // ios: WidgetShared/Models.swift:432
    const val name = "alpaca_quotes.json"   // ios: WidgetShared/Models.swift:433
    const val base = "https://data.alpaca.markets/v2/stocks/snapshots"   // ios: WidgetShared/Models.swift:434
    /** 설정 > 내 Alpaca 키에 보이는 상태: 마지막 성공 시각(초), 마지막 실패 이유 (성공하면 지움) */
    val okAt: Double get() = Store.defaults.getDouble("alpaca_ok_at")   // ios: WidgetShared/Models.swift:436
    val lastError: String? get() = Store.defaults.getString("alpaca_err", null)   // ios: WidgetShared/Models.swift:437
    private fun fail(why: String): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:438
        Store.defaults.edit().putString("alpaca_err", why).apply()   // ios: WidgetShared/Models.swift:439
        return cached()   // ios: WidgetShared/Models.swift:440
    }
    /** 15분 안에 받은 값만 (토스와 같다) */
    fun cached(): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:443
        val f = (if (AlpacaKeys.on) Store.read(QuoteFeed.File.serializer(), name) else null)?.takeIf { nowSec() - it.got < 900 } ?: return null   // ios: WidgetShared/Models.swift:444
        return f   // ios: WidgetShared/Models.swift:445
    }
    /** Yahoo 기호 → Alpaca 기호. 미국 종목만 (BRK-B → BRK.B, 토스 코드와 같은 꼴). 국내·지수·환율은 null */
    fun code(sym: String): String? {   // ios: WidgetShared/Models.swift:448
        val u = sym.uppercase()   // ios: WidgetShared/Models.swift:449
        if (u.endsWith(".KS") || u.endsWith(".KQ")) return null   // ios: WidgetShared/Models.swift:450
        return TossFeed.code(u)   // ios: WidgetShared/Models.swift:451
    }

    /** syms: 종목 → Yahoo 기호. 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 5초를 기다린다 */
    suspend fun fetch(syms: Map<String, String>, minAge: Double = 0.0): QuoteFeed.File? = withContext(Dispatchers.IO) { fetchIO(syms, minAge) }   // ios: WidgetShared/Models.swift:455,456
    private suspend fun fetchIO(syms: Map<String, String>, minAge: Double): QuoteFeed.File? {
        val k = AlpacaKeys.load() ?: return null   // ios: WidgetShared/Models.swift:457
        val ids = mutableMapOf<String, MutableList<String>>()   // Alpaca 기호 → 앱 종목들   // ios: WidgetShared/Models.swift:458
        for ((id, y) in syms) { code(y)?.let { c -> ids.getOrPut(c) { mutableListOf() }.add(id) } }   // ios: WidgetShared/Models.swift:459
        if (ids.isEmpty()) return null   // ios: WidgetShared/Models.swift:460
        cached()?.let { o -> if (nowSec() - o.got < minAge) return o }   // ios: WidgetShared/Models.swift:461
        val q = mutableMapOf<String, QuoteFeed.Q>()   // ios: WidgetShared/Models.swift:462
        val all = ids.keys.sorted()   // ios: WidgetShared/Models.swift:463
        for (i in 0 until all.size step 100) {   // ios: WidgetShared/Models.swift:464
            val part = all.subList(i, min(i + 100, all.size)).joinToString(",")   // ios: WidgetShared/Models.swift:465
            val enc = pctEncode(part, ".,")   // ios: WidgetShared/Models.swift:466
            val url = uriOrNull(base + "?symbols=" + enc + "&feed=iex") ?: return fail("잘못된 종목")   // ios: WidgetShared/Models.swift:467
            val (code, o, e) = get(url, k)   // ios: WidgetShared/Models.swift:468
            if (e != null) return fail(e)   // ios: WidgetShared/Models.swift:469
            if (code != 200 || o == null) return fail(why(code))   // ios: WidgetShared/Models.swift:470
            for ((c, v) in o) {   // ios: WidgetShared/Models.swift:471
                val s = jObj(v) ?: continue; val t = jObj(s["latestTrade"]) ?: continue; val last = num(t["p"])?.takeIf { it > 0 } ?: continue   // ios: WidgetShared/Models.swift:472
                val prev = num(jObj(s["prevDailyBar"])?.get("c")) ?: 0.0   // ios: WidgetShared/Models.swift:473
                val at = (t["t"] as? String)?.let { isoSec(it) } ?: nowSec()   // ios: WidgetShared/Models.swift:474
                for (id in ids[c.uppercase()] ?: emptyList()) q[id] = QuoteFeed.Q(last = last, prev = prev, at = at)   // ios: WidgetShared/Models.swift:475
            }
        }
        if (q.isEmpty()) return fail("받은 종목 없음")   // ios: WidgetShared/Models.swift:478
        val f = QuoteFeed.File(got = nowSec(), q = q)   // ios: WidgetShared/Models.swift:479
        Store.write(QuoteFeed.File.serializer(), f, name)   // ios: WidgetShared/Models.swift:480
        Store.defaults.edit().putDouble("alpaca_ok_at", f.got)   // ios: WidgetShared/Models.swift:481
            .remove("alpaca_err").apply()   // ios: WidgetShared/Models.swift:482
        return f   // ios: WidgetShared/Models.swift:483
    }

    private suspend fun get(url: String, k: AlpacaKeys.Keys): Triple<Int, Map<String, Any?>?, String?> {   // ios: WidgetShared/Models.swift:486
        val r = FeedReq(url, timeout = 5.0)   // ios: WidgetShared/Models.swift:487
        r.setValue(k.id, "APCA-API-KEY-ID")   // ios: WidgetShared/Models.swift:488
        r.setValue(k.secret, "APCA-API-SECRET-KEY")   // ios: WidgetShared/Models.swift:489
        try {   // ios: WidgetShared/Models.swift:490
            val (code, d) = r.send()   // ios: WidgetShared/Models.swift:491
            return Triple(code.coerceAtLeast(0), jObj(feedJson(d)), null)   // ios: WidgetShared/Models.swift:492
        } catch (e: IOException) { return Triple(0, null, if (e is SocketTimeoutException) "5초 안에 응답 없음" else "Alpaca에 닿지 못함") }   // ios: WidgetShared/Models.swift:493
        catch (e: Exception) { return Triple(0, null, "Alpaca에 닿지 못함") }   // ios: WidgetShared/Models.swift:494
    }

    fun why(code: Int): String {   // ios: WidgetShared/Models.swift:497
        if (code == 401 || code == 403) return "키가 맞지 않아요"   // ios: WidgetShared/Models.swift:498
        if (code == 429) return "잠시 뒤에 다시 받아요"   // ios: WidgetShared/Models.swift:499
        return "Alpaca 응답 $code"   // ios: WidgetShared/Models.swift:500
    }

    /** 키 확인: AAPL 하나를 물어 본다 (설정 화면 '연결하기') */
    suspend fun check(): String? {   // ios: WidgetShared/Models.swift:504
        val k = AlpacaKeys.load() ?: return "키 없음"; val url = base + "?symbols=AAPL&feed=iex"   // ios: WidgetShared/Models.swift:505
        val (code, _, e) = get(url, k)   // ios: WidgetShared/Models.swift:506
        return e ?: (if (code == 200) null else why(code))   // ios: WidgetShared/Models.swift:507
    }

    /** 위젯: 앱이 feed.json 에 넘긴 내 종목을 위젯이 직접 Alpaca 에 묻는다 */
    suspend fun widgetFetch(minAge: Double): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:511,512
        val ty = (if (AlpacaKeys.on) Store.read(WFeed.serializer(), "feed.json")?.ty else null)?.takeIf { it.isNotEmpty() } ?: return null   // ios: WidgetShared/Models.swift:513
        return fetch(ty, minAge = minAge)   // ios: WidgetShared/Models.swift:514
    }
}

/** 사용자가 넣은 Alpaca 키. 토스 키처럼 이 기기 Keystore 로 감싸 둔다 */
object AlpacaKeys {   // ios: WidgetShared/Models.swift:519
    @Serializable data class Keys(var id: String, var secret: String)   // ios: WidgetShared/Models.swift:520
    private fun q(): String =   // ios: WidgetShared/Models.swift:521
        KeyVault.slot("com.naeilo.alpaca", "keys")   // ios: WidgetShared/Models.swift:522
    private fun put(v: Keys?) {   // ios: WidgetShared/Models.swift:524
        KeyVault.delete(q())   // ios: WidgetShared/Models.swift:525
        if (v == null) return; val d = runCatching { NJson.encodeToString(Keys.serializer(), v).toByteArray(Charsets.UTF_8) }.getOrNull() ?: return   // ios: WidgetShared/Models.swift:526
        val a = q()   // ios: WidgetShared/Models.swift:527
        KeyVault.add(a, d)   // ios: WidgetShared/Models.swift:529,531
    }
    /** 키를 넣었는지 (위젯도 보므로 표시만 공유 저장소에 둔다) */
    val stored: Boolean get() = Store.defaults.getBoolean("alpaca_user", false)   // ios: WidgetShared/Models.swift:538
    /** 개발자용 잠시 멈춤 (내부 베타만): 키는 그대로 두고 없는 것처럼 안 부른다 */
    val paused: Boolean get() = Config.isInternal && Store.defaults.getBoolean("alpaca_pause", false)   // ios: WidgetShared/Models.swift:540
    val on: Boolean get() = stored && !paused   // ios: WidgetShared/Models.swift:541
    fun load(): Keys? {   // ios: WidgetShared/Models.swift:535
        if (!on) return null   // ios: WidgetShared/Models.swift:536
        val a = q()   // ios: WidgetShared/Models.swift:537
        val d = KeyVault.read(a) ?: return null   // ios: WidgetShared/Models.swift:535,542,543,544
        return runCatching { NJson.decodeFromString(Keys.serializer(), d.decodeToString()) }.getOrNull()   // ios: WidgetShared/Models.swift:545
    }
    fun save(k: Keys) {   // ios: WidgetShared/Models.swift:547
        put(k)   // ios: WidgetShared/Models.swift:548
        Store.defaults.edit().putBoolean("alpaca_user", true).apply()   // ios: WidgetShared/Models.swift:549
    }
    fun clear() {   // ios: WidgetShared/Models.swift:551
        put(null)   // ios: WidgetShared/Models.swift:552
        Store.defaults.edit().putBoolean("alpaca_user", false)   // ios: WidgetShared/Models.swift:553
            .remove("alpaca_err")   // ios: WidgetShared/Models.swift:554
            .remove("alpaca_ok_at").apply()   // ios: WidgetShared/Models.swift:555
        Store.remove(AlpacaFeed.name)   // ios: WidgetShared/Models.swift:556
    }
}

// Longbridge 지금 가격. 사용자가 open.longbridge.com 개발자 센터에서 받은 App Key·App Secret·Access Token 으로 Longbridge 를 직접 부른다 (중계 없음).
// 모의(paper) 계정에도 홍콩·미국·A주 기본 실시간 시세가 들어 있다. 공식 SDK 의 HTTP 방식(POST /quote/quotes, HMAC-SHA256 서명)을 그대로 쓴다.
// 앱은 아직 원화·달러 종목만 다루므로 지금은 미국 종목에 쓰인다. 홍콩(.HK)·상하이(.SS)·선전(.SZ) 기호도 알아듣게 해 두었다.
// 우선순위: 토스 키 > Alpaca 키 > Longbridge 키 > 사이트 시세. 전일 종가는 Alpaca 처럼 사이트(공식 종가) 값이 먼저다.
object LongbridgeFeed {   // ios: WidgetShared/Models.swift:607
    const val name = "longbridge_quotes.json"   // ios: WidgetShared/Models.swift:608
    const val base = "https://openapi.longbridge.com"   // ios: WidgetShared/Models.swift:609
    /** 설정 > 내 Longbridge 키에 보이는 상태: 마지막 성공 시각(초), 마지막 실패 이유 (성공하면 지움) */
    val okAt: Double get() = Store.defaults.getDouble("longbridge_ok_at")   // ios: WidgetShared/Models.swift:611
    val lastError: String? get() = Store.defaults.getString("longbridge_err", null)   // ios: WidgetShared/Models.swift:612
    private fun fail(why: String): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:613
        Store.defaults.edit().putString("longbridge_err", why).apply()   // ios: WidgetShared/Models.swift:614
        return cached()   // ios: WidgetShared/Models.swift:615
    }
    /** 15분 안에 받은 값만 (토스·Alpaca 와 같다) */
    fun cached(): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:618
        val f = (if (LongbridgeKeys.on) Store.read(QuoteFeed.File.serializer(), name) else null)?.takeIf { nowSec() - it.got < 900 } ?: return null   // ios: WidgetShared/Models.swift:619
        return f   // ios: WidgetShared/Models.swift:620
    }
    /** Yahoo 기호 → Longbridge 기호. AAPL → AAPL.US, BRK-B → BRK.B.US, 0700.HK → 700.HK, 600519.SS → 600519.SH, 000001.SZ → 000001.SZ. 국내·지수·환율은 null */
    fun code(sym: String): String? {   // ios: WidgetShared/Models.swift:623
        val u = sym.trim().uppercase()   // ios: WidgetShared/Models.swift:624
        val digits = { x: String -> x.isNotEmpty() && x.all { "0" <= it.toString() && it.toString() <= "9" } }   // ios: WidgetShared/Models.swift:625
        if (u.endsWith(".HK")) {   // ios: WidgetShared/Models.swift:626
            val c = u.dropLast(3)   // ios: WidgetShared/Models.swift:627
            if (!digits(c)) return null   // ios: WidgetShared/Models.swift:628
            val n = c.dropWhile { it.toString() == "0" }   // ios: WidgetShared/Models.swift:629
            return (if (n.isEmpty()) "0" else n) + ".HK"   // ios: WidgetShared/Models.swift:630
        }
        if (u.endsWith(".SS") || u.endsWith(".SZ")) {   // ios: WidgetShared/Models.swift:632
            val c = u.dropLast(3)   // ios: WidgetShared/Models.swift:633
            if (c.length != 6 || !digits(c)) return null   // ios: WidgetShared/Models.swift:634
            return c + (if (u.endsWith(".SS")) ".SH" else ".SZ")   // ios: WidgetShared/Models.swift:635
        }
        return AlpacaFeed.code(u)?.let { it + ".US" }   // ios: WidgetShared/Models.swift:637
    }

    /** syms: 종목 → Yahoo 기호. 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 5초를 기다린다 */
    suspend fun fetch(syms: Map<String, String>, minAge: Double = 0.0): QuoteFeed.File? = withContext(Dispatchers.IO) { fetchIO(syms, minAge) }   // ios: WidgetShared/Models.swift:641,642
    private suspend fun fetchIO(syms: Map<String, String>, minAge: Double): QuoteFeed.File? {
        val k = LongbridgeKeys.load() ?: return null   // ios: WidgetShared/Models.swift:643
        val ids = mutableMapOf<String, MutableList<String>>()   // Longbridge 기호 → 앱 종목들   // ios: WidgetShared/Models.swift:644
        for ((id, y) in syms) { code(y)?.let { c -> ids.getOrPut(c) { mutableListOf() }.add(id) } }   // ios: WidgetShared/Models.swift:645
        if (ids.isEmpty()) return null   // ios: WidgetShared/Models.swift:646
        cached()?.let { o -> if (nowSec() - o.got < minAge) return o }   // ios: WidgetShared/Models.swift:647
        val q = mutableMapOf<String, QuoteFeed.Q>()   // ios: WidgetShared/Models.swift:648
        val all = ids.keys.sorted()   // ios: WidgetShared/Models.swift:649
        for (i in 0 until all.size step 500) {   // 한 번에 500종목까지   // ios: WidgetShared/Models.swift:650
            val (o, e) = post("/quote/quotes", mapOf("symbol" to all.subList(i, min(i + 500, all.size))), k)   // ios: WidgetShared/Models.swift:651
            if (e != null) return fail(e)   // ios: WidgetShared/Models.swift:652
            for (v in jObjs(o?.get("secu_quote")) ?: emptyList()) {   // ios: WidgetShared/Models.swift:653
                val c = (v["symbol"] as? String)?.uppercase() ?: continue; val last = num(v["last_done"])?.takeIf { it > 0 } ?: continue   // ios: WidgetShared/Models.swift:654
                val at = num(v["timestamp"])?.takeIf { it > 0 } ?: nowSec()   // ios: WidgetShared/Models.swift:655
                for (id in ids[c] ?: emptyList()) q[id] = QuoteFeed.Q(last = last, prev = num(v["prev_close"]) ?: 0.0, at = at)   // ios: WidgetShared/Models.swift:656
            }
        }
        if (q.isEmpty()) return fail("받은 종목 없음")   // ios: WidgetShared/Models.swift:659
        val f = QuoteFeed.File(got = nowSec(), q = q)   // ios: WidgetShared/Models.swift:660
        Store.write(QuoteFeed.File.serializer(), f, name)   // ios: WidgetShared/Models.swift:661
        Store.defaults.edit().putDouble("longbridge_ok_at", f.got)   // ios: WidgetShared/Models.swift:662
            .remove("longbridge_err").apply()   // ios: WidgetShared/Models.swift:663
        return f   // ios: WidgetShared/Models.swift:664
    }

    /** 서명한 POST (기호 목록 본문). 응답 {code, message, data} 에서 data 만 돌려준다 */
    private suspend fun post(path: String, body: Map<String, List<String>>, k: LongbridgeKeys.Keys): Pair<Map<String, Any?>?, String?> =   // ios: WidgetShared/Models.swift:668
        postJson(path, JsonObject(body.mapValues { (_, v) -> JsonArray(v.map { JsonPrimitive(it) }) }), k)
    /** 서명한 POST (아무 JSON 본문. 일봉 FlowLongbridge·1분봉 LongbridgeMinutes 도 쓴다) */
    suspend fun postJson(path: String, body: JsonObject, k: LongbridgeKeys.Keys): Pair<Map<String, Any?>?, String?> {   // ios: WidgetShared/Models.swift:668
        val url = uriOrNull(base + path) ?: return null to "잘못된 요청"; val b = body.toString().toByteArray(Charsets.UTF_8)   // ios: WidgetShared/Models.swift:669
        val token = if (k.token.startsWith("Bearer ")) k.token.drop(7) else k.token   // ios: WidgetShared/Models.swift:670
        val ts = (nowSec() * 1000).toLong().toString()   // ios: WidgetShared/Models.swift:671
        val r = FeedReq(url, timeout = 5.0)   // ios: WidgetShared/Models.swift:672
        r.httpMethod = "POST"   // ios: WidgetShared/Models.swift:673
        r.httpBody = b   // ios: WidgetShared/Models.swift:674
        r.setValue("application/json; charset=utf-8", "content-type")   // ios: WidgetShared/Models.swift:675
        r.setValue(k.key, "x-api-key")   // ios: WidgetShared/Models.swift:676
        r.setValue(token, "authorization")   // ios: WidgetShared/Models.swift:677
        r.setValue(ts, "x-timestamp")   // ios: WidgetShared/Models.swift:678
        // 미국 데이터센터 계정은 키 앞에 us_ 가 붙는다 (공식 SDK 와 같게)
        r.setValue(if (listOf(k.key, k.secret, token).any { it.startsWith("us_") }) "us" else "ap", "x-dc-region")   // ios: WidgetShared/Models.swift:680
        r.setValue(sign(path = path, key = k.key, token = token, ts = ts, body = b, secret = k.secret), "x-api-signature")   // ios: WidgetShared/Models.swift:681
        try {   // ios: WidgetShared/Models.swift:682
            val (http0, d) = r.send()   // ios: WidgetShared/Models.swift:683
            val http = http0.coerceAtLeast(0)   // ios: WidgetShared/Models.swift:684
            val o = jObj(feedJson(d))   // ios: WidgetShared/Models.swift:685
            val code = o?.let { num(it["code"]) } ?: -1.0   // ios: WidgetShared/Models.swift:686
            if (http == 200 && code == 0.0) return (jObj(o?.get("data")) ?: emptyMap()) to null   // ios: WidgetShared/Models.swift:687
            return null to why(http, o?.get("message") as? String)   // ios: WidgetShared/Models.swift:688
        } catch (e: IOException) { return null to (if (e is SocketTimeoutException) "5초 안에 응답 없음" else "Longbridge에 닿지 못함") }   // ios: WidgetShared/Models.swift:689
        catch (e: Exception) { return null to "Longbridge에 닿지 못함" }   // ios: WidgetShared/Models.swift:690
    }

    /** 공식 SDK 서명: "방법|경로|쿼리|서명할 헤더들\n|헤더 이름들|본문 SHA1" 의 SHA1 을 "HMAC-SHA256|…" 로 묶어 App Secret 으로 HMAC */
    fun sign(path: String, key: String, token: String, ts: String, body: ByteArray, secret: String): String {   // ios: WidgetShared/Models.swift:694
        val names = "authorization;x-api-key;x-timestamp"   // ios: WidgetShared/Models.swift:695
        var plain = "POST|" + path + "||"   // ios: WidgetShared/Models.swift:696
        plain += "authorization:$token\nx-api-key:$key\nx-timestamp:$ts\n"   // ios: WidgetShared/Models.swift:697
        plain += "|" + names + "|"   // ios: WidgetShared/Models.swift:698
        if (body.isNotEmpty()) plain += hex(MessageDigest.getInstance("SHA-1").digest(body))   // ios: WidgetShared/Models.swift:699
        val text = "HMAC-SHA256|" + hex(MessageDigest.getInstance("SHA-1").digest(plain.toByteArray(Charsets.UTF_8)))   // ios: WidgetShared/Models.swift:700
        // 빈 비밀값은 HMAC 에서 0 한 바이트 키와 같다 (SecretKeySpec 은 빈 키를 받지 않는다)
        val mac = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8).let { if (it.isEmpty()) ByteArray(1) else it }, "HmacSHA256")); doFinal(text.toByteArray(Charsets.UTF_8)) }   // ios: WidgetShared/Models.swift:701
        return "HMAC-SHA256 SignedHeaders=$names, Signature=" + hex(mac)   // ios: WidgetShared/Models.swift:702
    }
    private fun hex(d: ByteArray): String = d.joinToString("") { String.format("%02x", it) }   // ios: WidgetShared/Models.swift:704

    fun why(http: Int, message: String?): String {   // ios: WidgetShared/Models.swift:706
        if (http == 401 || http == 403) return "키가 맞지 않거나 토큰 기한이 지났어요"   // ios: WidgetShared/Models.swift:707
        if (http == 429) return "잠시 뒤에 다시 받아요"   // ios: WidgetShared/Models.swift:708
        if (!message.isNullOrEmpty()) return "Longbridge: " + message.take(60)   // ios: WidgetShared/Models.swift:709
        return "Longbridge 응답 $http"   // ios: WidgetShared/Models.swift:710
    }

    /** 키 확인: AAPL 하나를 물어 본다 (설정 화면 '연결하기') */
    suspend fun check(): String? {   // ios: WidgetShared/Models.swift:714
        val k = LongbridgeKeys.load() ?: return "키 없음"   // ios: WidgetShared/Models.swift:715
        val (o, e) = post("/quote/quotes", mapOf("symbol" to listOf("AAPL.US")), k)   // ios: WidgetShared/Models.swift:716
        if (e != null) return e   // ios: WidgetShared/Models.swift:717
        return if ((o?.get("secu_quote") as? List<*>)?.isEmpty() == false) null else "시세 권한이 없어요"   // ios: WidgetShared/Models.swift:718
    }

    /** 위젯: 앱이 feed.json 에 넘긴 내 종목을 위젯이 직접 Longbridge 에 묻는다 */
    suspend fun widgetFetch(minAge: Double): QuoteFeed.File? {   // ios: WidgetShared/Models.swift:722,723
        val ty = (if (LongbridgeKeys.on) Store.read(WFeed.serializer(), "feed.json")?.ty else null)?.takeIf { it.isNotEmpty() } ?: return null   // ios: WidgetShared/Models.swift:724
        return fetch(ty, minAge = minAge)   // ios: WidgetShared/Models.swift:725
    }
}

/** 사용자가 넣은 Longbridge 키. Alpaca 키처럼 이 기기 Keystore 로 감싸 둔다 */
object LongbridgeKeys {   // ios: WidgetShared/Models.swift:730
    @Serializable data class Keys(var key: String, var secret: String, var token: String)   // ios: WidgetShared/Models.swift:731
    private fun q(): String =   // ios: WidgetShared/Models.swift:732
        KeyVault.slot("com.naeilo.longbridge", "keys")   // ios: WidgetShared/Models.swift:733
    private fun put(v: Keys?) {   // ios: WidgetShared/Models.swift:735
        KeyVault.delete(q())   // ios: WidgetShared/Models.swift:736
        if (v == null) return; val d = runCatching { NJson.encodeToString(Keys.serializer(), v).toByteArray(Charsets.UTF_8) }.getOrNull() ?: return   // ios: WidgetShared/Models.swift:737
        val a = q()   // ios: WidgetShared/Models.swift:738
        KeyVault.add(a, d)   // ios: WidgetShared/Models.swift:740,742
    }
    /** 키를 넣었는지 (위젯도 보므로 표시만 공유 저장소에 둔다) */
    val stored: Boolean get() = Store.defaults.getBoolean("longbridge_user", false)   // ios: WidgetShared/Models.swift:749
    /** 개발자용 잠시 멈춤 (내부 베타만): 키는 그대로 두고 없는 것처럼 안 부른다 */
    val paused: Boolean get() = Config.isInternal && Store.defaults.getBoolean("longbridge_pause", false)   // ios: WidgetShared/Models.swift:751
    val on: Boolean get() = stored && !paused   // ios: WidgetShared/Models.swift:752
    fun load(): Keys? {   // ios: WidgetShared/Models.swift:746
        if (!on) return null   // ios: WidgetShared/Models.swift:747
        val a = q()   // ios: WidgetShared/Models.swift:748
        val d = KeyVault.read(a) ?: return null   // ios: WidgetShared/Models.swift:743,753,754,755
        return runCatching { NJson.decodeFromString(Keys.serializer(), d.decodeToString()) }.getOrNull()   // ios: WidgetShared/Models.swift:756
    }
    fun save(k: Keys) {   // ios: WidgetShared/Models.swift:758
        put(k)   // ios: WidgetShared/Models.swift:759
        Store.defaults.edit().putBoolean("longbridge_user", true).apply()   // ios: WidgetShared/Models.swift:760
    }
    fun clear() {   // ios: WidgetShared/Models.swift:762
        put(null)   // ios: WidgetShared/Models.swift:763
        Store.defaults.edit().putBoolean("longbridge_user", false)   // ios: WidgetShared/Models.swift:764
            .remove("longbridge_err")   // ios: WidgetShared/Models.swift:765
            .remove("longbridge_ok_at").apply()   // ios: WidgetShared/Models.swift:766
        Store.remove(LongbridgeFeed.name)   // ios: WidgetShared/Models.swift:767
    }
}

// ── 키체인 자리 ──

private fun nowSec(): Double = System.currentTimeMillis() / 1000.0

/** 키체인 대신: Android Keystore 의 AES 키(이 기기에서만, 내보낼 수 없음)로 값을 AES/GCM 으로 감싸 SharedPreferences 에 둔다.
 *  백업으로 다른 기기에 옮겨져도 풀 키가 없으니 못 읽는다 (아이폰 ThisDeviceOnly 와 같은 뜻). 위젯은 같은 앱이라 같이 읽는다 */
object KeyVault {
    private const val ALIAS = "naeilo_keychain"
    private const val GCM = "AES/GCM/NoPadding"
    private const val IV = 12

    fun slot(service: String, account: String) = "kc.$service.$account"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun add(slot: String, d: ByteArray) {
        runCatching {
            val c = Cipher.getInstance(GCM)
            c.init(Cipher.ENCRYPT_MODE, key())
            val out = c.iv + c.doFinal(d)
            Store.defaults.edit().putString(slot, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        }
    }

    fun read(slot: String): ByteArray? = runCatching {
        val raw = Base64.decode(Store.defaults.getString(slot, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance(GCM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV))
        c.doFinal(raw, IV, raw.size - IV)
    }.getOrNull()

    fun delete(slot: String) { Store.defaults.edit().remove(slot).apply() }
}

// [String: Double] 를 UserDefaults 사전처럼: JSON 문자열로 둔다
private val dblMapSer = MapSerializer(String.serializer(), Double.serializer())
private fun SharedPreferences.dblMap(key: String): Map<String, Double> =
    getString(key, null)?.let { runCatching { NJson.decodeFromString(dblMapSer, it) }.getOrNull() } ?: emptyMap()
private fun SharedPreferences.Editor.putDblMap(key: String, v: Map<String, Double>): SharedPreferences.Editor =
    putString(key, NJson.encodeToString(dblMapSer, v))
