package com.naeilo.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naeilo.app.AppModel
import com.naeilo.app.core.NJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

// 인물 대사 데이터: assets/lines_ko.json · lines_app_en.json (tools/lines.py 가 만든다, 146개 상황 × 인물별 최대 100줄). 앱 언어가 영어면 영어 파일만 읽는다.
// 키는 "상황.인물" (예: "calm.2.sio", "fb.chase.ir", "item.barley_tea.sua"). 줄 안의 {n} 같은 자리는 값으로 채운다.
// 볼 때마다(화면을 열거나 앱을 앞으로 가져오거나 당겨서 갱신) 같은 상황에서 다음 줄을 고른다. 파일에 없는 키는 null 이라 부르는 쪽이 원래 문장을 쓴다.
// 안드로이드: 파일(0.8MB)은 처음 부를 때 IO 에서 읽는다 (화면 그리는 중에 읽지 않는다). 다 읽기 전에는 null 이라 원래 문장이 보이고, 읽으면 그 화면이 다시 그려진다.
// ios: Data/Lines.swift:7
object Lines {
    // ios: Data/Lines.swift:8
    @Serializable private class File(val lines: Map<String, List<String>>)
    private var loaded by mutableStateOf<Map<String, List<String>>?>(null)
    @Volatile private var started = false
    /** 읽은 대사 표. 시험(IParityTest)은 값을 바로 넣는다 */
    // ios: Data/Lines.swift:9
    internal var all: Map<String, List<String>>
        get() { loaded?.let { return it }; warm(); return emptyMap() }
        set(v) { loaded = v }

    /** 앱 언어에 맞는 파일 하나만 IO 에서 읽는다 (앱 시작 때 미리 불러도 된다) */
    fun warm() {
        if (started) return
        started = true
        CoroutineScope(Dispatchers.IO).launch {
            // 앱 언어에 맞는 파일 하나만 읽는다 (영어: lines_app_en.json, 그 밖: lines_ko.json)
            val v = runCatching { if (AppModel.en) load("lines_app_en") else load("lines_ko") }.getOrNull() ?: emptyMap()   // ios: Data/Lines.swift:11
            if (loaded == null) loaded = v   // ios: Data/Lines.swift:12
        }
    }
    // ios: Data/Lines.swift:13
    private fun load(name: String): Map<String, List<String>> {
        // ios: Data/Lines.swift:14,15
        val f = Defaults.asset(name, "json")?.let { s -> runCatching { NJson.decodeFromString(File.serializer(), s) }.getOrNull() } ?: return emptyMap()
        return f.lines   // ios: Data/Lines.swift:16
    }

    /** key 의 줄 하나. 볼 때(차례가 바뀐 뒤 처음 그릴 때)만 다음 줄로 넘어가서, 한 바퀴 돌 때까지 겹치지 않는다.
     *  salt 로 같은 화면의 다른 자리끼리 따로 센다 */
    // ios: Data/Lines.swift:21
    fun pick(key: String, vars: Map<String, String> = emptyMap(), salt: Int = 0): String? {
        val list = all[key]; if (list == null || list.isEmpty()) return null   // ios: Data/Lines.swift:22
        val id = if (salt == 0) key else "${key}#${salt}"   // ios: Data/Lines.swift:23
        val turn = LineClock.shared.turn   // ios: Data/Lines.swift:24
        val p = (LineClock.pos[id] ?: LineClock.Pos(turn = turn, i = start(key, list.size))).copy()   // ios: Data/Lines.swift:25
        if (p.turn != turn) { p.turn = turn; p.i += 1; LineClock.pos = LineClock.pos + (id to p); LineClock.save() }   // ios: Data/Lines.swift:26
        else if (LineClock.pos[id] == null) { LineClock.pos = LineClock.pos + (id to p); LineClock.save() }   // ios: Data/Lines.swift:27
        return fill(list[p.i % list.size], vars)   // ios: Data/Lines.swift:28
    }

    /** 지금 줄 다음부터 n줄 (넘기지 않는다). 위젯이 갱신할 때마다 하나씩 쓴다 */
    // ios: Data/Lines.swift:32
    fun peek(key: String, vars: Map<String, String> = emptyMap(), n: Int): List<String> {
        val list = all[key]; if (list == null || list.isEmpty()) return emptyList()   // ios: Data/Lines.swift:33
        val i = LineClock.pos[key]?.i ?: (start(key, list.size) - 1)   // ios: Data/Lines.swift:34
        return (1..minOf(n, list.size)).map { fill(list[(i + it) % list.size], vars) }   // ios: Data/Lines.swift:35
    }

    /** 위젯에서 본 만큼 앞으로 넘긴다 */
    // ios: Data/Lines.swift:39
    fun skip(key: String, n: Int) {
        val list = all[key]; if (n <= 0 || list == null || list.isEmpty()) return   // ios: Data/Lines.swift:40
        val p = (LineClock.pos[key] ?: LineClock.Pos(turn = LineClock.shared.turn, i = start(key, list.size) - 1)).copy()   // ios: Data/Lines.swift:41
        p.i += n; LineClock.pos = LineClock.pos + (key to p); LineClock.save()   // ios: Data/Lines.swift:42
    }

    /** 처음 줄은 키마다 다르게 (같은 화면의 인물들이 같은 번호부터 시작하지 않게). FNV-1a 64비트 (넘침은 버린다) */
    // ios: Data/Lines.swift:46
    internal fun start(key: String, n: Int): Int {
        var h: ULong = 1469598103934665603L.toULong()   // ios: Data/Lines.swift:47
        for (b in key.encodeToByteArray()) { h = (h xor b.toUByte().toULong()) * 1099511628211L.toULong() }   // ios: Data/Lines.swift:48
        return (h % n.toULong()).toInt()   // ios: Data/Lines.swift:49
    }
    // ios: Data/Lines.swift:51
    private fun fill(line: String, vars: Map<String, String>): String {
        var s = line   // ios: Data/Lines.swift:52
        for ((k, v) in vars) s = s.replace("{$k}", v)   // ios: Data/Lines.swift:53
        return s   // ios: Data/Lines.swift:54
    }

    /** 전체 줄 수 (설정 등에서 보여 줄 때) */
    val count: Int get() = all.values.fold(0) { a, l -> a + l.size }   // ios: Data/Lines.swift:58
}

/** 대사 차례: 화면을 열거나, 앱을 앞으로 가져오거나, 당겨서 갱신할 때 하나씩 오른다.
 *  1분 자동 갱신으로는 오르지 않아서, 보지 않은 대사를 건너뛰지 않는다 */
// ios: Data/Lines.swift:63,64 (@Observable: turn 은 Compose 상태)
class LineClock {
    // ios: Data/Lines.swift:66
    var turn by mutableStateOf(runCatching { Defaults.prefs.getInt("lineTurn", 0) }.getOrDefault(0))
        private set
    fun next() { turn += 1; runCatching { Defaults.prefs.edit().putInt("lineTurn", turn).apply() } }   // ios: Data/Lines.swift:67

    // ios: Data/Lines.swift:69
    @Serializable data class Pos(var turn: Int, var i: Int)

    companion object {
        val shared = LineClock()   // ios: Data/Lines.swift:65
        /** 상황·인물별로 마지막에 보여 준 줄 (UserDefaults 의 JSON Data 자리: 같은 JSON 을 글자로) */
        // ios: Data/Lines.swift:71
        var pos: Map<String, Pos> = run {
            val d = runCatching { Defaults.prefs.getString("linePos", null) }.getOrNull()   // ios: Data/Lines.swift:72
            val p = d?.let { runCatching { NJson.decodeFromString<Map<String, Pos>>(it) }.getOrNull() } ?: return@run emptyMap()
            p   // ios: Data/Lines.swift:73,74
        }
        // ios: Data/Lines.swift:75
        fun save() { val d = runCatching { NJson.encodeToString(pos) }.getOrNull(); if (d != null) runCatching { Defaults.prefs.edit().putString("linePos", d).apply() } }
    }
}
