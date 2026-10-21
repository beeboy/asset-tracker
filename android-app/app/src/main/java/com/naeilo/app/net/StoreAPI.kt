package com.naeilo.app.net

import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.data.StoryBook
import com.naeilo.app.data.StoryChapter
import com.naeilo.app.data.StoryLang
import com.naeilo.app.data.Support
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.net.URL

// 구독 원고 서버 (naeilo 중계 워커의 /store/*). 앱은 구독의 구매 확인 값을 그대로 보내고,
// 서버가 서명과 구독을 확인한 뒤 유료 원고를 건넨다. 개발자 빌드에서 개발자 동기화 토큰이 있으면 구매 없이 받는다.
//   POST /store/book {jws, book:"side"|"vol1", lang}   → { book:{lang, from, chapters} }   (구독 모자람 403, 원고 없음 404)
object StoreAPI {   // ios: Data/StoreAPI.swift:7
    @Serializable data class Book(val lang: String, val from: Int, val chapters: List<StoryChapter>)   // ios: Data/StoreAPI.swift:8

    sealed class Fail(msg: String) : Exception(msg) {   // ios: Data/StoreAPI.swift:10,12,13
        class tier : Fail("구독하면 열려요.")   // ios: Data/StoreAPI.swift:11,14
        class missing : Fail("원고가 아직 올라오지 않았어요. 준비되면 여기서 바로 받을 수 있어요.")   // ios: Data/StoreAPI.swift:15
        class server(s: String) : Fail(s)   // ios: Data/StoreAPI.swift:16
    }

    @Serializable private data class BookReply(val book: Book)   // ios: Data/StoreAPI.swift:21
    @Serializable private data class ErrorReply(val error: String? = null)   // ios: Data/StoreAPI.swift:22

    fun url(path: String): String = URL(URL(Config.relay, "store" + "/"), path).toString()   // ios: Data/StoreAPI.swift:24

    private suspend fun <T> post(path: String, body: Map<String, Any?>, t: KSerializer<T>): T = withContext(Dispatchers.IO) {   // ios: Data/StoreAPI.swift:26,27
        val b = body.toMutableMap()   // ios: Data/StoreAPI.swift:28
        b["jws"] = Support.shared.jws   // ios: Data/StoreAPI.swift:29
        val req = FeedReq(url(path), timeout = 30.0)   // ios: Data/StoreAPI.swift:30
        req.httpMethod = "POST"   // ios: Data/StoreAPI.swift:31
        req.setValue("application/json", "Content-Type")   // ios: Data/StoreAPI.swift:32
        // 개발자 빌드만: 개발자 동기화 토큰이면 구매 없이 (스토어 빌드에서는 보내지 않는다)
        val tk = Sync.shared.creds?.token; if (Support.shared.testBuild && tk != null) req.setValue("Bearer " + tk, "Authorization")   // ios: Data/StoreAPI.swift:34
        req.httpBody = plainToJson(b).toString().encodeToByteArray()   // ios: Data/StoreAPI.swift:35
        val (res, d) = req.send()   // ios: Data/StoreAPI.swift:36
        val code = res.coerceAtLeast(0)   // ios: Data/StoreAPI.swift:37
        if (code == 403) throw Fail.tier()   // ios: Data/StoreAPI.swift:38
        if (code == 404) throw Fail.missing()   // ios: Data/StoreAPI.swift:39
        if (code !in 200 until 300) {   // ios: Data/StoreAPI.swift:40
            throw Fail.server(runCatching { NJson.decodeFromString(ErrorReply.serializer(), d.decodeToString()) }.getOrNull()?.error ?: "서버에 닿지 못했어요 (${code}).")   // ios: Data/StoreAPI.swift:41
        }
        NJson.decodeFromString(t, d.decodeToString())   // ios: Data/StoreAPI.swift:43
    }

    suspend fun book(id: StoryBook, lang: StoryLang): Book {   // ios: Data/StoreAPI.swift:46
        val r: BookReply = post("book", mapOf("book" to id.name, "lang" to lang.name), BookReply.serializer())   // ios: Data/StoreAPI.swift:47
        return r.book   // ios: Data/StoreAPI.swift:48
    }
}
