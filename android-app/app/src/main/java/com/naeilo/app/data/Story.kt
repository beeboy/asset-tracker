package com.naeilo.app.data

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.AppModel
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Store
import com.naeilo.app.core.WL
import com.naeilo.app.core.WStory
import com.naeilo.app.net.StoreAPI
import com.naeilo.app.pending.WidgetCenter
import com.naeilo.app.pending.reloadTimelines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.math.max
import kotlin.math.min

// 서재 원고: 외전 『이종 공명』 프롤로그~5장 (한글 원고 + 영문 번역본). 사이트 공개 범위와 같아서 앱에 같이 넣는다.
// assets/story_ko.json · story_en.json (iOS Resources 와 같은 파일, tools/story.py 가 MyVault 원고에서 만든다). 두 언어는 블록이 하나씩 맞아서
// 언어를 바꿔도 읽던 자리(블록 번호)가 그대로다.
// 6장~코다(구독)와 앱 연재판 1권(연 구독 또는 월 구독 9개월 이상, 한글·영문)은 서버에서 받아(StoreAPI) 이 기기에 저장해 둔다.
// 안드로이드: 파일 읽기는 화면 그리는 중에 하지 않는다. 앱에 든 원고·받아 둔 원고는 IO 에서 읽고, 다 읽으면 그 화면이 다시 그려진다
// (메인 스레드가 아닌 곳, 예: 위젯 쓰기에서 부르면 그 자리에서 바로 읽는다).

// ios: Data/Story.swift:11
@Serializable
data class StoryBlock(
    val k: String,           // ios: Data/Story.swift:12 h 절 제목, q 인용(화면·메시지), p 문단, s 장면 나눔, t 표
    val t: String,  // ios: Data/Story.swift:13
    var rows: List<List<String>>? = null,  // ios: Data/Story.swift:14
)
// ios: Data/Story.swift:16
@Serializable
data class StoryChapter(
    val title: String, val name: String, val blocks: List<StoryBlock>,  // ios: Data/Story.swift:17
    var cover: String? = null,  // ios: Data/Story.swift:18 서버에서 받은 장의 도트 그림 (128×80 PNG data URL). 앱에 든 장은 drawable 의 그림을 쓴다
)
// ios: Data/Story.swift:20
@Serializable private data class StoryFile(var lang: String, var from: Int? = null, val chapters: List<StoryChapter>)

// ios: Data/Story.swift:22 (경우 이름 = iOS rawValue)
enum class StoryLang { ko, en;
    val label: String get() = if (this == ko) "한" else "EN"   // ios: Data/Story.swift:23
}

/** 외전(side) 또는 본편 1권(vol1). 경우 이름 = iOS rawValue */
// ios: Data/Story.swift:27
enum class StoryBook { side, vol1;
    val langs: List<StoryLang> get() = listOf(StoryLang.ko, StoryLang.en)   // ios: Data/Story.swift:28
    /** 앱에 들어 있는 장 수 (외전 프롤로그~5장) */
    val bundled: Int get() = if (this == side) 6 else 0   // ios: Data/Story.swift:30
    val route: String get() = if (this == side) "read:" else "vol1:"   // ios: Data/Story.swift:31
}

// ios: Data/Story.swift:34,35 (@MainActor @Observable: 화면이 읽는 값은 Compose 상태)
class Story {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 읽기 화면에서 고른 언어. null 이면 기기 언어를 따른다 */
    // ios: Data/Story.swift:39
    private var pickedS by mutableStateOf(Defaults.prefs.getString("storyLang", null)?.let { s -> StoryLang.entries.firstOrNull { it.name == s } })
    var picked: StoryLang?
        get() = pickedS
        // ios: Data/Story.swift:40
        set(v) { pickedS = v; Defaults.prefs.edit().apply { if (v == null) remove("storyLang") else putString("storyLang", v.name) }.apply() }
    val lang: StoryLang get() = picked ?: deviceLang   // ios: Data/Story.swift:42
    /** 그 책에서 실제로 읽는 언어 */
    fun lang(b: StoryBook): StoryLang = if (b.langs.contains(lang)) lang else StoryLang.ko   // ios: Data/Story.swift:44
    /** 토글: 기기 언어를 고르면 다시 '기기 언어 따라가기'로 */
    fun pick(l: StoryLang) { picked = if (l == deviceLang) null else l }   // ios: Data/Story.swift:48

    /** 장마다 읽던 블록 번호 (이 기기에만 둔다). 외전은 장 번호, 본편 1권은 1000 + 장 번호. 저장은 {"장 키": 블록} JSON 글자 */
    // ios: Data/Story.swift:51
    var pos by mutableStateOf(runCatching { NJson.decodeFromString<Map<String, Int>>(Defaults.prefs.getString("storyPos", null) ?: "{}") }.getOrNull().orEmpty()
        .entries.fold(mapOf<Int, Int>()) { d, kv -> kv.key.toIntOrNull()?.let { d + (it to kv.value) } ?: d })   // ios: Data/Story.swift:52
        private set
    /** 끝까지 읽은 장 (서재 묶음이 저절로 접히고 펼쳐지는 데 쓴다). 키는 pos 와 같다. 저장은 JSON 배열 글자 */
    // ios: Data/Story.swift:55
    var done by mutableStateOf(runCatching { NJson.decodeFromString<List<Int>>(Defaults.prefs.getString("storyDone", null) ?: "[]") }.getOrNull().orEmpty().toSet())
        private set
    fun isDone(b: StoryBook, chapter: Int): Boolean = done.contains(key(b, chapter))   // ios: Data/Story.swift:56
    // ios: Data/Story.swift:57
    fun markDone(b: StoryBook, chapter: Int) {
        if (done.contains(key(b, chapter))) return; done = done + key(b, chapter)   // ios: Data/Story.swift:58
        Defaults.prefs.edit().putString("storyDone", NJson.encodeToString(done.toList())).apply()   // ios: Data/Story.swift:59
    }
    /** 가장 최근에 읽은 장 (외전·본편 1권 통틀어 하나). 서재는 이 장이 든 묶음만 펼친다. 키는 pos 와 같다 */
    // ios: Data/Story.swift:62
    var last by mutableStateOf(if (Defaults.prefs.contains("storyLast")) Defaults.prefs.getInt("storyLast", 0) else null)
        private set
    // ios: Data/Story.swift:63
    fun touch(b: StoryBook, chapter: Int) {
        val k = key(b, chapter)   // ios: Data/Story.swift:64
        if (last == k) return   // ios: Data/Story.swift:65
        last = k   // ios: Data/Story.swift:66
        Defaults.prefs.edit().putInt("storyLast", k).apply()   // ios: Data/Story.swift:67
    }
    // ios: Data/Story.swift:69
    fun setPos(b: StoryBook, chapter: Int, block: Int) {
        touch(b, chapter)   // ios: Data/Story.swift:70
        val k = key(b, chapter)   // ios: Data/Story.swift:71
        if (pos[k] == block) return   // ios: Data/Story.swift:72
        pos = pos + (k to block)   // ios: Data/Story.swift:73
        Defaults.prefs.edit().putString("storyPos", NJson.encodeToString(pos.entries.associate { it.key.toString() to it.value })).apply()   // ios: Data/Story.swift:74
    }

    // ios: Data/Story.swift:77 (화면이 다시 그려지게 상태로 둔다)
    private var bundledBooks by mutableStateOf(mapOf<StoryLang, List<StoryChapter>>())
    private val bundledAsked = mutableSetOf<StoryLang>()
    /** 서버에서 받은 장: "side_ko" → (첫 장 번호, 장들) */
    private class Remote(val from: Int, val chapters: List<StoryChapter>)
    // ios: Data/Story.swift:79 (받아 둔 원고는 IO 에서 읽어 넣는다)
    private var remote by mutableStateOf(mapOf<String, Remote>())
    private val cache = scope.async(Dispatchers.IO) { loadCache() }
    init { scope.launch { val c = cache.await(); remote = c + remote } }
    /** 받는 중인 책 ("side_ko"), 받다가 난 오류 */
    // ios: Data/Story.swift:81
    var loading by mutableStateOf(setOf<String>())
        private set
    // ios: Data/Story.swift:82
    var failure by mutableStateOf(mapOf<String, String>())
        private set
    private val recheck = mutableSetOf<String>()   // ios: Data/Story.swift:83

    // ios: Data/Story.swift:87
    fun chapter(i: Int, b: StoryBook = StoryBook.side, l0: StoryLang? = null): StoryChapter? {
        val l = l0 ?: lang(b)   // ios: Data/Story.swift:88
        if (b == StoryBook.side && i < b.bundled) {   // ios: Data/Story.swift:89
            if (bundledBooks[l] == null) warm(l)
            return bundledBooks[l]?.let { if (i < it.size) it[i] else null }   // ios: Data/Story.swift:95
        }
        // 번역본을 아직 못 받았으면 한글 원고로 읽는다
        val r = remote[id(b, l)] ?: remote[id(b, StoryLang.ko)]; if (r == null || i < r.from || i - r.from >= r.chapters.size) return null   // ios: Data/Story.swift:98
        return r.chapters[i - r.from]   // ios: Data/Story.swift:99
    }
    /** 앱에 든 원고 읽기: 메인 스레드면 IO 에서 읽고 다 읽으면 다시 그리게, 아니면 그 자리에서 */
    private fun warm(l: StoryLang) {
        if (Looper.myLooper() != Looper.getMainLooper()) { loadBundled(l)?.let { bundledBooks = bundledBooks + (l to it) }; return }
        if (!bundledAsked.add(l)) return
        scope.launch { val c = withContext(Dispatchers.IO) { loadBundled(l) }; if (c != null) bundledBooks = bundledBooks + (l to c) }
    }
    // ios: Data/Story.swift:90,91,92
    private fun loadBundled(l: StoryLang): List<StoryChapter>? = Defaults.asset("story_${l.name}", "json")?.let { d -> runCatching { NJson.decodeFromString(StoryFile.serializer(), d) }.getOrNull() }
        ?.chapters   // ios: Data/Story.swift:93
    /** 지금 읽을 수 있는 장 수 (앱에 든 것 + 받아 둔 것) */
    // ios: Data/Story.swift:102
    fun count(b: StoryBook, l0: StoryLang? = null): Int {
        val l = l0 ?: lang(b)   // ios: Data/Story.swift:103
        val r = remote[id(b, l)] ?: remote[id(b, StoryLang.ko)] ?: return b.bundled   // ios: Data/Story.swift:104
        return max(b.bundled, r.from + r.chapters.size)   // ios: Data/Story.swift:105
    }
    fun isLoading(b: StoryBook): Boolean = loading.contains(id(b, lang(b)))   // ios: Data/Story.swift:107
    fun failText(b: StoryBook): String? = failure[id(b, lang(b))]   // ios: Data/Story.swift:108

    /** 서버에서 받기 (구독이 되면). 받은 것은 이 기기에 저장해서 다음부터는 바로 연다 */
    // ios: Data/Story.swift:111
    suspend fun fetch(b: StoryBook, force: Boolean = false) {
        remote = cache.await() + remote   // 저장해 둔 원고를 다 읽은 뒤에 따진다
        val l = lang(b); val k = id(b, l)   // ios: Data/Story.swift:112
        // 그림(cover)이 없던 예전 캐시는 앱을 켤 때마다 한 번 다시 받아 본다
        val stale = remote[k]?.let { r -> r.chapters.all { it.cover == null } } == true && !recheck.contains(k)   // ios: Data/Story.swift:114
        if (!(Support.shared.open(b) && !loading.contains(k) && (force || remote[k] == null || stale))) return   // ios: Data/Story.swift:115
        recheck.add(k)   // ios: Data/Story.swift:116
        loading = loading + k; failure = failure - k   // ios: Data/Story.swift:117
        try {   // ios: Data/Story.swift:118,119 (defer → finally)
            val r = StoreAPI.book(b, l)   // ios: Data/Story.swift:120
            val got = StoryLang.entries.firstOrNull { it.name == r.lang } ?: l   // ios: Data/Story.swift:121
            remote = remote + (id(b, got) to Remote(r.from, r.chapters))   // ios: Data/Story.swift:122
            withContext(Dispatchers.IO) { saveCache(id(b, got), StoryFile(lang = r.lang, from = r.from, chapters = r.chapters)) }   // ios: Data/Story.swift:123
        } catch (error: Exception) {   // ios: Data/Story.swift:124
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (remote[k] == null || force) failure = failure + (k to (error.message ?: error.toString()))   // ios: Data/Story.swift:125 받아 둔 원고가 있으면 다시 받기 실패는 조용히
        } finally { loading = loading - k }
    }

    /** 머리글 "외전 『이종 공명』 · 1장" / "Resonance Across Kinds · Chapter 1" / "『중첩된 현실』 1권 · 1부 제1장" / "Nested Reality, Vol. 1 · Part 1, Chapter 1" */
    // ios: Data/Story.swift:153
    fun heading(i: Int, b: StoryBook = StoryBook.side): String {
        val t = chapter(i, b)?.title ?: (if (b == StoryBook.side && i < 6) Shelter.chapters[i].title else "")   // ios: Data/Story.swift:154
        if (b == StoryBook.vol1) return if (chapter(i, b)?.title?.startsWith("Part") == true) "Nested Reality, Vol. 1 · $t" else "『중첩된 현실』 1권 · $t"   // ios: Data/Story.swift:155
        return if (lang == StoryLang.ko) "외전 『이종 공명』 · $t" else "Resonance Across Kinds · $t"   // ios: Data/Story.swift:156
    }

    /** 위젯에 넘길 값: 마지막으로 연 장의 읽던 자리 (앱에 든 외전 프롤로그~5장). 메인 스레드 밖(IO)에서 부르면 원고를 바로 읽는다 */
    // ios: Data/Story.swift:160
    fun widget(m: AppModel): WStory {
        val i = min(m.readLast ?: 0, 5); val open = m.chapterOn(i)   // ios: Data/Story.swift:161
        val ch = chapter(i); val blocks = ch?.blocks ?: emptyList()   // ios: Data/Story.swift:162
        val at = min(pos[i] ?: 0, max(0, blocks.size - 1))   // ios: Data/Story.swift:163
        // 읽던 자리부터 처음 나오는 문단·인용 (절 제목·표는 건너뛴다)
        val text = blocks.indices.drop(at).firstOrNull { listOf("p", "q").contains(blocks[it].k) }?.let { blocks[it].t } ?: ""   // ios: Data/Story.swift:165
        return WStory(open = open, chapter = i,   // ios: Data/Story.swift:166
            label = (ch?.title ?: Shelter.chapters[i].title) + " · " + (ch?.name ?: Shelter.chapters[i].name),   // ios: Data/Story.swift:167
            line = if (open) plain(text) else "앱 시작 3단계를 마치면 서재가 열려요.",   // ios: Data/Story.swift:168
            friend = Shelter.chapters[i].friend,   // ios: Data/Story.swift:169
            progress = if (blocks.isEmpty()) 0.0 else at.toDouble() / blocks.size.toDouble(),   // ios: Data/Story.swift:170
            lang = lang.name)   // ios: Data/Story.swift:171
    }

    /** 서재 위젯만 다시 그린다 (읽던 자리·언어가 바뀌었을 때). 파일 쓰기라 IO 에서 */
    // ios: Data/Story.swift:184
    fun pushWidget(m: AppModel) {
        scope.launch(Dispatchers.IO) {
            pushArt(m)   // ios: Data/Story.swift:185
            Store.write(widget(m), "story.json")   // ios: Data/Story.swift:186
            WidgetCenter.reloadTimelines(ofKind = "story")   // ios: Data/Story.swift:187
        }
    }

    companion object {
        val shared: Story by lazy { Story() }   // ios: Data/Story.swift:36
        /** 기기 언어 목록의 첫 번째가 한국어면 한글, 아니면 영문 */
        val deviceLang: StoryLang get() = if (WL.en) StoryLang.en else StoryLang.ko   // ios: Data/Story.swift:46 위젯 문구와 같은 규칙
        fun key(b: StoryBook, i: Int): Int = if (b == StoryBook.side) i else 1000 + i   // ios: Data/Story.swift:53

        private fun id(b: StoryBook, l: StoryLang): String = "${b.name}_${l.name}"   // ios: Data/Story.swift:85

        // 받은 원고 저장: 앱 데이터 폴더 app_story/side_ko.json (Application Support 자리. 다른 앱은 못 읽어 따로 잠금 보호는 없다)
        // ios: Data/Story.swift:130
        private val cacheDir: File get() {
            val d = Defaults.ctx.getDir("story", 0)   // ios: Data/Story.swift:131 (0 = MODE_PRIVATE: 앱 데이터 폴더의 app_story)
            d.mkdirs()   // ios: Data/Story.swift:132
            return d   // ios: Data/Story.swift:133
        }
        // ios: Data/Story.swift:135
        private fun saveCache(id: String, f: StoryFile) {
            runCatching { NJson.encodeToString(StoryFile.serializer(), f) }.getOrNull()?.let { d -> runCatching { File(cacheDir, id + ".json").writeText(d) } }   // ios: Data/Story.swift:136
        }
        // ios: Data/Story.swift:138
        private fun loadCache(): Map<String, Remote> {
            val out = mutableMapOf<String, Remote>()   // ios: Data/Story.swift:139
            for (b in listOf(StoryBook.side, StoryBook.vol1)) {   // ios: Data/Story.swift:140
                for (l in b.langs) {   // ios: Data/Story.swift:141
                    val id = id(b, l)   // ios: Data/Story.swift:142
                    val d = runCatching { File(cacheDir, id + ".json").readText() }.getOrNull()   // ios: Data/Story.swift:143
                    val f = d?.let { runCatching { NJson.decodeFromString(StoryFile.serializer(), it) }.getOrNull() }   // ios: Data/Story.swift:144
                    if (f != null) out[id] = Remote(f.from ?: b.bundled, f.chapters)   // ios: Data/Story.swift:145
                }
            }
            return out   // ios: Data/Story.swift:149
        }

        /** 서재 위젯 장 그림: 마지막으로 연 장의 표지 도트 그림을 앱 폴더에 png 로 둔다 (바뀔 때만). 파일 읽기·쓰기라 IO 에서 부른다 */
        // ios: Data/Story.swift:175
        fun pushArt(m: AppModel) {
            val name = Shelter.cover(min(m.readLast ?: 0, 5))   // ios: Data/Story.swift:176
            if (!(Store.defaults.getString("storyArt", null) != name || Store.readData(WStory.artFile) == null)) return   // ios: Data/Story.swift:177
            // ios: Data/Story.swift:178 UIImage(named:).pngData() 자리: drawable-nodpi 의 png 파일 그대로
            val d = runCatching { val ctx = Store.ctx; val rid = ctx.resources.getIdentifier(name, "drawable", ctx.packageName); if (rid == 0) null else ctx.resources.openRawResource(rid).use { it.readBytes() } }.getOrNull() ?: return
            Store.writeData(d, WStory.artFile)   // ios: Data/Story.swift:179
            Store.defaults.edit().putString("storyArt", name).apply()   // ios: Data/Story.swift:180
        }

        /** **굵게**·_기울임_·~~지움~~ 기호를 뺀 글 (위젯은 짧게 한 문단만). AttributedString(markdown:, inlineOnlyPreservingWhitespace) 자리 */
        // ios: Data/Story.swift:191
        fun plain(s: String): String {
            val a = stripInline(s)   // ios: Data/Story.swift:192
            return a.replace("\n", " ")   // ios: Data/Story.swift:193
        }
        /** 줄 안 마크다운 기호만 뺀다: [글](주소) → 글, `코드`, **·__ 굵게, *·_ 기울임, ~~ 지움, \ 이스케이프 */
        private fun stripInline(s: String): String {
            var t = s
            t = Regex("""!?\[([^\]]*)]\([^)]*\)""").replace(t) { it.groupValues[1] }
            t = Regex("`([^`]*)`").replace(t) { it.groupValues[1] }
            t = Regex("""\*\*(.+?)\*\*""").replace(t) { it.groupValues[1] }
            t = Regex("""(?<![\w\\])__(.+?)__(?!\w)""").replace(t) { it.groupValues[1] }
            t = Regex("""~~(.+?)~~""").replace(t) { it.groupValues[1] }
            t = Regex("""(?<![\w\\*])\*(?!\s)(.+?)(?<!\s)\*(?!\*)""").replace(t) { it.groupValues[1] }
            t = Regex("""(?<![\w\\])_(?!\s)(.+?)(?<!\s)_(?!\w)""").replace(t) { it.groupValues[1] }
            t = Regex("""\\([\\`*_{}\[\]()#+\-.!~])""").replace(t) { it.groupValues[1] }
            return t
        }
    }
}
