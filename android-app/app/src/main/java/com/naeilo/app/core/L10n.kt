package com.naeilo.app.core

import androidx.compose.ui.text.AnnotatedString
import com.naeilo.app.AppModel
import org.json.JSONObject

/** 화면 글자 영어로: 아이폰 Localizable.xcstrings 의 en 을 그대로 쓴다 (한국어 원문 = 키).
 *  표는 tools/l10n/gen_en.py 가 안드로이드 코드가 실제로 쓰는 것만 골라 assets/l10n_en.json 으로 만든다.
 *  한국어 기기면 아무것도 하지 않고 그대로 돌려준다 (표도 읽지 않는다). */
fun tr(s: String): String = if (!AppModel.en) s else L10n.tr(s)

/** 꾸민 글(굵게 등): 꾸밈 경계로 나눈 토막마다 바꾼다 */
fun tr(a: AnnotatedString): AnnotatedString = if (!AppModel.en) a else L10n.tr(a)

object L10n {
    /** 자리 있는 글: 조각들(자리 사이 글자)과 영어 틀({0} = 첫 자리 값) */
    private class Pat(val chunks: Array<String>, val en: String) { val lit = chunks.sumOf { it.length }; val loose = chunks[0].isEmpty() && chunks[chunks.size - 1].isEmpty() }

    private class Table(val exact: HashMap<String, String>, val byFirst: HashMap<Char, ArrayList<Pat>>, val slotFirst: ArrayList<Pat>, val frags: List<String>)

    // 처음 쓸 때 한 번만 읽는다
    private val table: Table by lazy {
        val exact = HashMap<String, String>(4096); val byFirst = HashMap<Char, ArrayList<Pat>>(); val slotFirst = ArrayList<Pat>(); val frags = ArrayList<String>()
        try {
            val o = JSONObject(Store.ctx.assets.open("l10n_en.json").use { it.readBytes().decodeToString() })
            val x = o.getJSONObject("x"); val ks = x.keys()
            while (ks.hasNext()) { val k = ks.next(); exact[k] = x.getString(k) }
            val p = o.getJSONArray("p")
            for (i in 0 until p.length()) {
                val e = p.getJSONArray(i); val c = e.getJSONArray(0)
                val pat = Pat(Array(c.length()) { c.getString(it) }, e.getString(1))
                if (pat.chunks[0].isEmpty()) slotFirst.add(pat) else byFirst.getOrPut(pat.chunks[0][0]) { ArrayList() }.add(pat)
            }
            val f = o.getJSONArray("f")
            for (i in 0 until f.length()) frags.add(f.getString(i))
        } catch (_: Exception) {}
        Table(exact, byFirst, slotFirst, frags)
    }

    // 바꾼 결과 기억 (같은 글은 다시 찾지 않는다). 숫자가 계속 바뀌는 글로 너무 커지면 비운다
    private val cache = HashMap<String, String>(1024)
    private val cacheA = HashMap<AnnotatedString, AnnotatedString>(64)

    fun tr(s: String): String {
        if (!hasHangul(s)) return s
        synchronized(cache) { cache[s]?.let { return it } }
        val r = find(s, 0)
        synchronized(cache) { if (cache.size > 4000) cache.clear(); cache[s] = r }
        return r
    }

    fun tr(a: AnnotatedString): AnnotatedString {
        if (!hasHangul(a.text)) return a
        synchronized(cacheA) { cacheA[a]?.let { return it } }
        // 꾸밈 경계마다 토막을 나눠 따로 바꾸고, 꾸밈을 새 위치에 다시 붙인다
        val cuts = sortedSetOf(0, a.length)
        a.spanStyles.forEach { cuts.add(it.start); cuts.add(it.end) }
        a.paragraphStyles.forEach { cuts.add(it.start); cuts.add(it.end) }
        val c = cuts.toIntArray(); val map = IntArray(c.size)
        val sb = StringBuilder()
        for (i in c.indices) {
            map[i] = sb.length
            if (i + 1 < c.size) sb.append(tr(a.text.substring(c[i], c[i + 1])))
        }
        fun at(o: Int) = map[c.indexOf(o)]
        val b = AnnotatedString.Builder(sb.toString())
        a.spanStyles.forEach { b.addStyle(it.item, at(it.start), at(it.end)) }
        a.paragraphStyles.forEach { b.addStyle(it.item, at(it.start), at(it.end)) }
        val r = b.toAnnotatedString()
        synchronized(cacheA) { if (cacheA.size > 500) cacheA.clear(); cacheA[a] = r }
        return r
    }

    private fun hasHangul(s: String): Boolean {
        for (ch in s) if (ch in '가'..'힣' || ch in 'ㄱ'..'ㆎ') return true
        return false
    }

    private fun find(s: String, depth: Int): String {
        val t = table
        t.exact[s]?.let { return it }
        matchPat(s, depth, false)?.let { return it }
        // 앞뒤 빈칸·끝의 › 는 두고 가운데만
        val a = s.indexOfFirst { !it.isWhitespace() }; val z = s.indexOfLast { !it.isWhitespace() && it != '›' }
        if (a >= 0 && z >= a && (a > 0 || z < s.length - 1)) {
            val mid = s.substring(a, z + 1)
            (t.exact[mid] ?: matchPat(mid, depth, false))?.let { return s.substring(0, a) + it + s.substring(z + 1) }
        }
        if (depth < 3) {
            // 여러 줄을 이어 붙인 글은 줄마다
            if (s.indexOf('\n') >= 0) {
                val r = s.split('\n').joinToString("\n") { if (hasHangul(it)) find(it, depth + 1) else it }
                if (r != s) return r
            }
            // "a · b" 로 이어 붙인 글은 토막마다 (" · 다음 %@" 처럼 앞에 · 가 붙은 키도 본다)
            if (s.contains(" · ")) {
                val ps = s.split(" · ")
                val sb = StringBuilder()
                for ((i, p) in ps.withIndex()) {
                    if (i == 0) { sb.append(if (hasHangul(p)) find(p, depth + 1) else p); continue }
                    val withDot = " · $p"
                    val r = if (hasHangul(p)) find(withDot, depth + 1) else withDot
                    sb.append(if (r != withDot) r else " · " + (if (hasHangul(p)) find(p, depth + 1) else p))
                }
                val r = sb.toString()
                if (r != s) return r
            }
        }
        matchPat(s, depth, true)?.let { return it }
        if (depth < 5) {
            // "미국" + " 2" 처럼 끝이 숫자 등(한글 없음)이면 앞만
            val sp = s.lastIndexOf(' ')
            if (sp > 0 && !hasHangul(s.substring(sp))) t.exact[s.substring(0, sp)]?.let { return it + s.substring(sp) }
            // "a" + x + "b" 처럼 이어 붙인 글: 아는 조각(긴 것부터)을 찾아 그 앞뒤는 다시 찾는다
            for (f in t.frags) {
                val i = s.indexOf(f)
                if (i < 0) continue
                val en = t.exact[f] ?: continue
                val head = s.substring(0, i); val tail = s.substring(i + f.length)
                return (if (hasHangul(head)) find(head, depth + 1) else head) + en + (if (hasHangul(tail)) find(tail, depth + 1) else tail)
            }
        }
        return s
    }

    /** 자리 있는 틀 중 맞는 것: 조각 글자가 가장 긴 것 */
    private fun matchPat(s: String, depth: Int, loose: Boolean): String? {
        if (s.isEmpty()) return null
        val t = table
        var best: Pat? = null; var bestVals: Array<String>? = null
        fun tryAll(l: List<Pat>?) {
            if (l == null) return
            for (p in l) {
                if (best != null && p.lit <= best!!.lit) continue
                // 앞뒤가 다 자리인 틀(%@까지 %@)은 아무 글에나 맞으니 다른 방법이 다 안 될 때만, 값도 짧게
                if (p.loose != loose) continue
                val v = capture(p.chunks, s, loose) ?: continue
                best = p; bestVals = v
            }
        }
        tryAll(t.byFirst[s[0]]); tryAll(t.slotFirst)
        val p = best ?: return null; val v = bestVals!!
        // 영어 틀의 {n} 에 값을 넣는다 (값에 한국어가 남았으면 그것도 바꾼다)
        val e = p.en; val sb = StringBuilder(e.length + 16); var i = 0
        while (i < e.length) {
            val ch = e[i]
            if (ch == '{' && i + 1 < e.length && e[i + 1] == '{') { sb.append('{'); i += 2; continue }
            if (ch == '}' && i + 1 < e.length && e[i + 1] == '}') { sb.append('}'); i += 2; continue }
            if (ch == '{') {
                val j = e.indexOf('}', i)
                val n = if (j > i) e.substring(i + 1, j).toIntOrNull() else null
                if (n != null && n < v.size) {
                    val x = v[n]
                    sb.append(if (depth < 2 && hasHangul(x)) find(x, depth + 1) else x); i = j + 1; continue
                }
            }
            sb.append(ch); i++
        }
        return sb.toString()
    }

    /** 값으로 보기엔 너무 큰 토막 (이어 붙인 다른 글까지 삼킨 것) */
    private fun tooBig(v: String) = v.indexOf('\n') >= 0 || v.contains(" · ")

    /** 조각을 왼쪽부터 차례로 찾아 사이 값을 잡는다. 값은 비면 안 된다 */
    private fun capture(c: Array<String>, s: String, loose: Boolean): Array<String>? =
        capture(c, s, loose, true)?.takeIf { v -> v.all { x -> x.isEmpty() || x.any { it.isLetterOrDigit() } } } ?: capture(c, s, loose, false)

    /** wide: 빈칸뿐인 조각(" ")은 뒤쪽 것을 고른다 ("내 실제 투자가 3%p 앞서요" 에서 앞 값이 빈칸을 품게) */
    private fun capture(c: Array<String>, s: String, loose: Boolean, wide: Boolean): Array<String>? {
        val first = c[0]; val last = c[c.size - 1]
        if (!s.startsWith(first) || !s.endsWith(last) || s.length < first.length + last.length + 1) return null
        val end = s.length - last.length
        val out = Array(c.size - 1) { "" }
        var pos = first.length; var k = 1; var slot = 0
        while (k <= c.size - 1) {
            // 빈 조각으로 붙은 자리(%@%@)는 한 토막으로 잡고 나눈다
            var n = 1
            while (k < c.size - 1 && c[k].isEmpty()) { n++; k++ }
            val j = if (k == c.size - 1) end else if (wide && c[k].isBlank()) s.lastIndexOf(c[k], end - 1).takeIf { it > pos } ?: -1 else s.indexOf(c[k], pos + 1)
            if (j < 0 || j > end || j - pos < 1) return null
            val v = s.substring(pos, j)
            if (tooBig(v) || (loose && (v.length > 24 || v.contains(", ") || v.indexOf('요') >= 0))) return null
            if (n == 1) out[slot] = v else split(v, n).forEachIndexed { i, x -> out[slot + i] = x }
            slot += n
            pos = if (k == c.size - 1) end else j + c[k].length
            k++
        }
        return out
    }

    /** 붙은 자리 나누기: 앞의 부호(+3.2 → + / 3.2), 뒤의 조사(실제가 → 실제 / 없음, 영어엔 조사가 없다) */
    private fun split(v: String, n: Int): List<String> {
        val r = MutableList(n) { "" }
        if (v.length >= 2 && v[0] in "+-−±▲▼") { r[0] = v.substring(0, 1); r[1] = v.substring(1) }
        else if (v.length >= 2 && v.last() in "이가은는을를와과로의" && hasHangul(v.substring(0, v.length - 1))) r[0] = v.substring(0, v.length - 1)
        else r[0] = v
        return r
    }
}
