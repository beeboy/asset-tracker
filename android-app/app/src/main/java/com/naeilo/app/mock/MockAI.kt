package com.naeilo.app.mock

import com.naeilo.app.core.cformat
import com.naeilo.app.core.jObj
import com.naeilo.app.core.jObjs
import com.naeilo.app.core.jArr
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

// AI 조언 (Claude). AI 는 조언자다: 규칙 장부를 고치지 않고, 자기 장부(AI 장부, 1,000만원)를 따로 굴린다.
// 키·한도·쓴 돈은 개발자 서버(server.py /api/mock/ai)에 있다. 여기서는 물음을 만들고, 답을 받아 지킬 것을 다시 본다.

/** AI 부르는 때와 한도 (설정에서 드링커님이 정한다) */
@Serializable
data class MockAISettings(   // ios: Mock/MockAI.swift:8
    var ledgerEvery: Int = 10,      // ios: Mock/MockAI.swift:9
    var reviewEach: Boolean = true,    // ios: Mock/MockAI.swift:10
    var daily: Boolean = true,         // ios: Mock/MockAI.swift:11
    // 말로 쓴 조건은 누를 때만 부른다 (설정 없음)
    var day: Double = 4.0,            // ios: Mock/MockAI.swift:13
    var week: Double = 18.0,   // ios: Mock/MockAI.swift:14
    var month: Double = 80.0,   // ios: Mock/MockAI.swift:15
) {
    val limits: Map<String, Double> get() = mapOf("day" to day, "week" to week, "month" to month)   // ios: Mock/MockAI.swift:16
}

/** 서버가 알려 준 쓴 돈 (달러) */
@Serializable
data class MockAIWallet(   // ios: Mock/MockAI.swift:20
    var day: Double = 0.0, var week: Double = 0.0, var month: Double = 0.0,   // ios: Mock/MockAI.swift:21
    var at: MockDate,   // ios: Mock/MockAI.swift:22
    var key: Boolean = true,                 // ios: Mock/MockAI.swift:23
)

/** 규칙이 고른 종목 하나에 대한 AI 의견 */
@Serializable
data class MockAIView(   // ios: Mock/MockAI.swift:27
    var code: String,   // ios: Mock/MockAI.swift:28
    var view: String,               // ios: Mock/MockAI.swift:29
    var why: String,   // ios: Mock/MockAI.swift:30
) {
    val name: String get() = when (view) {   // ios: Mock/MockAI.swift:31,32
        "agree" -> "찬성"   // ios: Mock/MockAI.swift:33
        "drop" -> "빼자"   // ios: Mock/MockAI.swift:34
        else -> "글쎄"   // ios: Mock/MockAI.swift:35
    }
}

object MockAI {   // ios: Mock/MockAI.swift:40
    /** AI 장부가 낼 주문 하나 (앱이 다시 확인한 뒤) */
    data class Plan(   // ios: Mock/MockAI.swift:42
        var code: String,   // ios: Mock/MockAI.swift:43
        var name: String,   // ios: Mock/MockAI.swift:44
        var side: MockSide,   // ios: Mock/MockAI.swift:45
        var won: Double,            // ios: Mock/MockAI.swift:46
        var qty: Int?,              // ios: Mock/MockAI.swift:47
        var why: String,   // ios: Mock/MockAI.swift:48
    )

    const val maxOrders = 3   // ios: Mock/MockAI.swift:51
    /** AI 스위치 하나. 끄면 AI 화면(계좌 탭 AI 조언·AI 매매 자리·AI 알약)과 부르기가 모두 멈춘다 (2026-11-05). 참으로 바꾸면 다시 돈다 */
    const val on = false   // ios: Mock/MockAI.swift:53

    // 물음 만들기 (같은 장면이면 같은 글이 되게 시각은 날짜까지만)

    fun ledgerPrompt(l: MockLedger, prices: Map<String, Double>, hits: List<MockHit>, bundleName: String?,   // ios: Mock/MockAI.swift:57
                     excluded: Map<String, String>, day: String): String? {   // ios: Mock/MockAI.swift:58
        val cands = hits.filter { excluded[it.code] == null }.take(30)   // ios: Mock/MockAI.swift:59
        if (cands.isEmpty() && l.positions.isEmpty()) return null   // ios: Mock/MockAI.swift:60
        val eq = MockCalc.equity(l, prices)   // ios: Mock/MockAI.swift:61
        var t = "$day 한국 장중. 너의 장부(AI 장부): 시작 ${MockFmt.won(l.startCash)}, 지금 평가 ${MockFmt.won(eq)}, 쓸 수 있는 현금 ${MockFmt.won(l.cash)}.\n"   // ios: Mock/MockAI.swift:62
        t += "보유:\n"   // ios: Mock/MockAI.swift:63
        if (l.positions.isEmpty()) t += "- 없음\n"   // ios: Mock/MockAI.swift:64
        for (p in l.positions.values.sortedBy { it.code }) {   // ios: Mock/MockAI.swift:65
            val now = prices[p.code] ?: p.avg   // ios: Mock/MockAI.swift:66
            t += "- ${p.code} ${p.name} ${p.qty}주 · 평단 ${MockFmt.won(p.avg)} · 지금 ${MockFmt.won(now)} · ${MockFmt.pct(if (p.avg > 0) now / p.avg - 1 else 0.0)} · 산 날 ${p.openedDay}\n"   // ios: Mock/MockAI.swift:67
        }
        if (l.pending.isNotEmpty()) {   // ios: Mock/MockAI.swift:69
            t += "대기 주문:\n"   // ios: Mock/MockAI.swift:70
            for (o in l.pending) t += "- ${o.code} ${o.name} ${o.side.label} ${o.qty}주\n"   // ios: Mock/MockAI.swift:71
        }
        t += "후보 (관찰 결과 위쪽" + (bundleName?.let { ", 조건 묶음 '$it'" } ?: "") + "):\n"   // ios: Mock/MockAI.swift:73
        if (cands.isEmpty()) t += "- 없음\n"   // ios: Mock/MockAI.swift:74
        for (h in cands) {   // ios: Mock/MockAI.swift:75
            t += "- ${h.code} ${h.name} · ${MockFmt.won(prices[h.code] ?: h.price)}" + (h.change?.let { " · " + MockFmt.pct(it, digits = 1) } ?: "") + " · ${h.why}\n"   // ios: Mock/MockAI.swift:76
        }
        t += listOf(   // ios: Mock/MockAI.swift:78
            "할 일: 이 장부로 지금 살 것과 팔 것을 정해 orders 에 넣어라.",   // ios: Mock/MockAI.swift:79
            "- 매수: side buy, won 은 쓸 돈(원, 쓸 수 있는 현금 이하), qty 0. 후보 밖 종목, 이미 보유한 종목, 대기 주문이 있는 종목은 사지 않는다.",
            "- 매도: side sell, 보유 종목만, qty 는 팔 주 수(0 = 전부), won 0.",
            "- 한 번에 ${maxOrders}건 이하. 할 게 없으면 orders 를 비운다.",   // ios: Mock/MockAI.swift:82
            "note 에 지금 판단을 한 문장으로 쓴다.",   // ios: Mock/MockAI.swift:83
        ).joinToString("\n")
        return t   // ios: Mock/MockAI.swift:85
    }

    fun reviewPrompt(picks: List<MockCalc.Pick>, bundleName: String?, day: String): String? {   // ios: Mock/MockAI.swift:88
        if (picks.isEmpty()) return null   // ios: Mock/MockAI.swift:89
        var t = "$day. 규칙이 관찰 결과에서 아래 종목을 골랐다" + (bundleName?.let { " (조건 묶음 '$it')" } ?: "") + ".\n"   // ios: Mock/MockAI.swift:90
        for (p in picks) {   // ios: Mock/MockAI.swift:91
            t += "- ${p.code} ${p.name} · ${p.rank}위 · ${MockFmt.won(p.price)} · 비중 ${cformat("%.0f%%", p.weight * 100)} · ${p.why}\n"
        }
        t += "할 일: 종목마다 view 를 agree(찬성)·doubt(글쎄)·drop(빼자) 중 하나로 정하고 why 를 쓴다. 주문은 바꾸지 않는다. note 에 고른 묶음 전체에 대한 한 문장."   // ios: Mock/MockAI.swift:94
        return t   // ios: Mock/MockAI.swift:95
    }

    fun dailyPrompt(day: String, lines: List<String>): String =   // ios: Mock/MockAI.swift:98
        "$day 장 마감 뒤 모의투자 하루 기록이다.\n" + lines.joinToString("\n") +   // ios: Mock/MockAI.swift:99
            "\n할 일: summary 에 오늘을 두 문장으로 정리하고, points 에 규칙·손·AI 를 비교해 내일 볼 것을 세 개 이하로 쓴다."   // ios: Mock/MockAI.swift:100

    fun wordsPrompt(words: String): String = listOf(   // ios: Mock/MockAI.swift:103,104
        "국내 주식 관찰 조건을 말로 받았다: \"$words\"",   // ios: Mock/MockAI.swift:105
        "이것을 조건 조각으로 바꿔라. 조각 종류:",   // ios: Mock/MockAI.swift:106
        "- price: 지금 가격(원). lo·hi 는 원",   // ios: Mock/MockAI.swift:107
        "- change: 오늘 등락률(%). 2 = +2%",
        "- volume: 오늘 거래량 ÷ 지난 days 일 평균 (배)",   // ios: Mock/MockAI.swift:109
        "- ma: 지금 가격이 days 일 이동평균 위(above true)·아래(false). lo·hi 는 쓰지 않는다",   // ios: Mock/MockAI.swift:110
        "- amount: 오늘 거래대금(억원)",   // ios: Mock/MockAI.swift:111
        "lo 를 쓰면 hasLo true, 안 쓰면 hasLo false 와 lo 0. hi 도 같다. days 는 volume·ma 만 뜻이 있고 나머지는 20.",
        "조각은 모두 맞아야 걸린다. 말에 없는 조건은 넣지 않는다. sort 는 결과를 줄 세울 기준(큰 순). name 은 짧은 이름, note 는 바꾸면서 버린 말이 있으면 한 문장.",   // ios: Mock/MockAI.swift:113
    ).joinToString("\n")   // ios: Mock/MockAI.swift:114

    // 뒤 흐름 읽기 (단추로만 부른다. AI 장부와 따로 켠다, 2026-10-29)

    /** 뒤 흐름 읽기를 부르나. 2026-11-05 AI 를 모두 꺼 두며 on 하나로 묶는다 (on 을 참으로 바꾸면 함께 켜진다) */
    val flowOn: Boolean get() = on   // ios: Mock/MockAI.swift:120
    /** 하나씩 보낼 최근 판 수. 그 앞은 판 이유별로 묶어 보낸다 */
    const val afterMax = 15   // ios: Mock/MockAI.swift:122

    private fun r(p: Double?, p0: Double): String = p?.let { MockFmt.pct(if (p0 > 0) it / p0 - 1 else 0.0, digits = 1) } ?: "—"   // ios: Mock/MockAI.swift:124

    private fun flowHead(book: MockBook, day: String): String =   // ios: Mock/MockAI.swift:126
        "$day. ${if (book == MockBook.real) "실전 장부(실제 돈)" else "모의 장부(가상 돈)"}의 단타 기록이다. 하루 안에 사고팔고 15:00에 모두 정리하는 규칙으로 돈다.\n" +
            "읽는 사람은 장기 투자만 해 봤고 단타는 처음이다. 사고팔 판단은 그 사람이 한다.\n"   // ios: Mock/MockAI.swift:128

    /** 서버에 새 꼴을 두지 않고 하루 정리 꼴(summary · points)로 받는다. summary = 읽기, points = "낱말 || 뜻" */
    private val flowAsk = listOf(   // ios: Mock/MockAI.swift:132
        "할 일: 이 흐름에서 무엇이 보이는지 읽어 준다. 사라·팔아라·규칙을 바꾸라는 말은 하지 않는다.",   // ios: Mock/MockAI.swift:133
        "- summary: 숫자를 들어 쉬운 말 다섯 문장 이하. 단타 말(반환점·갭·투매·흔들림·되밀림 같은 것)은 이 기록에 맞춰 풀어 쓴다.",   // ios: Mock/MockAI.swift:134
        "- points: summary 에 쓴 단타 말을 세 개 이하, 한 줄에 하나 \"낱말 || 뜻(한 문장)\".",
        "- —로 빈 값은 앱이 꺼져 못 잰 것이니 짐작하지 않는다.",   // ios: Mock/MockAI.swift:136
    ).joinToString("\n")   // ios: Mock/MockAI.swift:137

    data class FB(val f: MockFollow, val bought: Boolean)

    /** 걸린 종목 뒤 흐름 물음과 읽은 범위 */
    fun followPrompt(book: MockBook, follows: List<FB>, day: String): Pair<String, String>? {   // ios: Mock/MockAI.swift:140
        if (follows.isEmpty()) return null   // ios: Mock/MockAI.swift:141
        var t = flowHead(book, day) + "장중 조건에 처음 걸린 종목의 뒤 흐름 (처음 걸린 값 대비 30분 · 1시간 · 마감, 날마다 산 것·안 산 것 평균):\n"   // ios: Mock/MockAI.swift:142
        val days = follows.groupBy { it.f.day }.entries.sortedBy { it.key }   // ios: Mock/MockAI.swift:143
        for ((d, v) in days) {   // ios: Mock/MockAI.swift:144
            for ((name, xs) in listOf("산 것" to v.filter { it.bought }, "안 산 것" to v.filter { !it.bought })) { if (xs.isEmpty()) continue   // ios: Mock/MockAI.swift:145
                fun avg(k: (MockFollow) -> Double?): String {   // ios: Mock/MockAI.swift:146
                    val rs = xs.mapNotNull { it.f.ret(k(it.f)) }   // ios: Mock/MockAI.swift:147
                    return if (rs.isEmpty()) "—" else MockFmt.pct(rs.sum() / rs.size.toDouble(), digits = 1)
                }
                t += "- $d $name ${xs.size}종목 · 30분 ${avg { it.p30 }} · 1시간 ${avg { it.p60 }} · 마감 ${avg { it.pEnd }}\n"
            }
        }
        days.lastOrNull()?.let { last ->   // ios: Mock/MockAI.swift:153
            t += "가장 최근 날(${last.key}) 종목별:\n"   // ios: Mock/MockAI.swift:154
            for (v in last.value.take(30)) {   // ios: Mock/MockAI.swift:155
                val f = v.f   // ios: Mock/MockAI.swift:156
                t += "- ${MockFmt.time(f.at)} ${f.name} 처음 ${MockFmt.won(f.p0)} · 30분 ${r(f.p30, f.p0)} · 1시간 ${r(f.p60, f.p0)} · 마감 ${r(f.pEnd, f.p0)} · ${if (v.bought) "산 것" else "안 산 것"}\n"
            }
        }
        val range = (days.firstOrNull()?.let { MockFmt.md(it.key) } ?: "") + "~" + (days.lastOrNull()?.let { MockFmt.md(it.key) } ?: "") + " · ${follows.size}종목"   // ios: Mock/MockAI.swift:160
        return (t + flowAsk) to range   // ios: Mock/MockAI.swift:161
    }

    data class XE(val x: MockAfter, val e: MockEvent?)

    /** 판 뒤 흐름 물음과 읽은 범위 */
    fun afterPrompt(book: MockBook, sells: List<XE>, older: List<MockAfter>, day: String): Pair<String, String>? {   // ios: Mock/MockAI.swift:165
        if (sells.isEmpty()) return null   // ios: Mock/MockAI.swift:166
        var t = flowHead(book, day) + "판 종목의 뒤 흐름 (판 값 대비 판 뒤 30분 · 1시간 · 마감 · 다음 날 시가):\n"   // ios: Mock/MockAI.swift:167
        for (s in sells) {   // ios: Mock/MockAI.swift:168
            val x = s.x   // ios: Mock/MockAI.swift:169
            t += "- ${x.day} ${MockFmt.time(x.at)} ${x.name} ${x.exit} · ${x.qty}주 · 판 값 ${MockFmt.won(x.p0)}"   // ios: Mock/MockAI.swift:170
            s.e?.let { e ->   // ios: Mock/MockAI.swift:171
                e.pnl?.let { pnl -> t += " · 손익 ${MockFmt.won(pnl)}" }   // ios: Mock/MockAI.swift:172
                e.buyWhy?.let { b -> if (b.isNotEmpty()) t += " · 산 이유 ${b.take(60)}" }   // ios: Mock/MockAI.swift:173
                e.exitWhy?.let { w -> if (w.isNotEmpty()) t += " · 판 이유 ${w.take(80)}" }   // ios: Mock/MockAI.swift:174
            }
            t += " · 30분 ${r(x.p30, x.p0)} · 1시간 ${r(x.p60, x.p0)} · 마감 ${r(x.pEnd, x.p0)} · 다음 날 시가 ${r(x.pOpen, x.p0)}\n"   // ios: Mock/MockAI.swift:176
        }
        val done = older.filter { it.ret != null }   // ios: Mock/MockAI.swift:178
        if (done.isNotEmpty()) {   // ios: Mock/MockAI.swift:179
            t += "그 앞 판 종목 (판 이유별, 마지막으로 잰 값 기준):\n"   // ios: Mock/MockAI.swift:180
            for ((k, v) in done.groupBy { it.exit }.entries.sortedByDescending { it.value.size }) {   // ios: Mock/MockAI.swift:181
                val rs = v.mapNotNull { it.ret }   // ios: Mock/MockAI.swift:182
                t += "- $k ${v.size}건 · 판 뒤 내림 ${rs.count { it < -0.003 }} · 오름 ${rs.count { it > 0.003 }} · 평균 ${MockFmt.pct(rs.sum() / rs.size.toDouble(), digits = 1)}\n"
            }
        }
        val all = older + sells.map { it.x }   // ios: Mock/MockAI.swift:186
        val range = MockFmt.md(all.firstOrNull()?.day ?: "") + "~" + MockFmt.md(all.lastOrNull()?.day ?: "") + " · 판 ${all.size}건"   // ios: Mock/MockAI.swift:187
        return (t + flowAsk) to range   // ios: Mock/MockAI.swift:188
    }

    fun flowRead(a: Map<String, Any?>, kind: MockFlowRead.Kind, book: MockBook, range: String): MockFlowRead {   // ios: Mock/MockAI.swift:191
        val r = MockFlowRead(kind = kind, book = book, at = mockNow(), range = range, text = ((a["summary"] as? String) ?: "").take(600))   // ios: Mock/MockAI.swift:192
        val terms = mutableListOf<List<String>>()
        for (p in (jArr(a["points"]) ?: emptyList()).mapNotNull { it as? String }.take(3)) {   // ios: Mock/MockAI.swift:193
            val f = p.split("||").map { it.trim() }   // ios: Mock/MockAI.swift:194
            if (f.size < 2 || f[0].isEmpty()) continue   // ios: Mock/MockAI.swift:195
            terms.add(listOf(f[0].take(20), f.drop(1).joinToString(" ").take(120)))   // ios: Mock/MockAI.swift:196
        }
        r.terms = terms
        return r   // ios: Mock/MockAI.swift:198
    }

    // 답 확인 (AI 가 정해진 꼴로 답해도 지킬 것은 앱이 다시 본다)

    data class Plans(val plans: List<Plan>, val dropped: List<String>, val note: String)

    fun ledgerPlans(answer: Map<String, Any?>, ledger: MockLedger, candidates: Map<String, String>,   // ios: Mock/MockAI.swift:203
                    excluded: Map<String, String>): Plans {   // ios: Mock/MockAI.swift:204
        val l = ledger
        val plans = mutableListOf<Plan>(); val dropped = mutableListOf<String>()   // ios: Mock/MockAI.swift:205
        var cash = l.cash   // ios: Mock/MockAI.swift:206
        for (o in jObjs(answer["orders"]) ?: emptyList()) {   // ios: Mock/MockAI.swift:207
            val code = ((o["code"] as? String) ?: "").trim()   // ios: Mock/MockAI.swift:208
            val why = ((o["why"] as? String) ?: "").take(80)   // ios: Mock/MockAI.swift:209
            if (plans.size >= maxOrders) { dropped.add("$code · ${maxOrders}건 넘음"); continue }   // ios: Mock/MockAI.swift:210
            when (o["side"] as? String) {   // ios: Mock/MockAI.swift:211
                "buy" -> {   // ios: Mock/MockAI.swift:212
                    val name = candidates[code]; if (name == null) { dropped.add("$code · 후보 밖"); continue }   // ios: Mock/MockAI.swift:213
                    excluded[code]?.let { ex -> dropped.add("$code · 예외 · $ex") }; if (excluded[code] != null) continue   // ios: Mock/MockAI.swift:214
                    if (l.positions[code] != null || l.pending.any { it.code == code } || plans.any { it.code == code }) {   // ios: Mock/MockAI.swift:215
                        dropped.add("$code · 이미 보유·대기"); continue   // ios: Mock/MockAI.swift:216
                    }
                    val won = min(MockFeed.num(o["won"]) ?: 0.0, cash)   // ios: Mock/MockAI.swift:218
                    if (won <= 0) { dropped.add("$code · 쓸 돈 없음"); continue }   // ios: Mock/MockAI.swift:219
                    cash -= won   // ios: Mock/MockAI.swift:220
                    plans.add(Plan(code = code, name = name, side = MockSide.buy, won = won, qty = null, why = why))   // ios: Mock/MockAI.swift:221
                }
                "sell" -> {   // ios: Mock/MockAI.swift:222
                    val p = l.positions[code]; if (p == null) { dropped.add("$code · 보유 아님"); continue }   // ios: Mock/MockAI.swift:223
                    if (plans.any { it.code == code }) { dropped.add("$code · 겹침"); continue }   // ios: Mock/MockAI.swift:224
                    val q = (MockFeed.num(o["qty"]) ?: 0.0).toInt()   // ios: Mock/MockAI.swift:225
                    plans.add(Plan(code = code, name = p.name, side = MockSide.sell, won = 0.0, qty = if (q > 0 && q < p.qty) q else null, why = why))   // ios: Mock/MockAI.swift:226
                }
                else -> dropped.add("$code · 모르는 주문")   // ios: Mock/MockAI.swift:227,228
            }
        }
        return Plans(plans, dropped, ((answer["note"] as? String) ?: "").take(200))   // ios: Mock/MockAI.swift:231
    }

    fun reviewViews(answer: Map<String, Any?>, picks: List<MockCalc.Pick>): Map<String, MockAIView> {   // ios: Mock/MockAI.swift:234
        val codes = picks.map { it.code }.toSet()   // ios: Mock/MockAI.swift:235
        val out = mutableMapOf<String, MockAIView>()   // ios: Mock/MockAI.swift:236
        for (i in jObjs(answer["items"]) ?: emptyList()) {   // ios: Mock/MockAI.swift:237
            val c = i["code"] as? String; if (c == null || c !in codes) continue   // ios: Mock/MockAI.swift:238
            val v = (i["view"] as? String) ?: "doubt"   // ios: Mock/MockAI.swift:239
            out[c] = MockAIView(code = c, view = if (v in listOf("agree", "doubt", "drop")) v else "doubt", why = ((i["why"] as? String) ?: "").take(80))   // ios: Mock/MockAI.swift:240
        }
        return out   // ios: Mock/MockAI.swift:242
    }

    /** 말 → 조건 묶음. 조각이 하나도 없으면 null */
    fun wordsBundle(answer: Map<String, Any?>, words: String): Pair<MockBundle, String>? {   // ios: Mock/MockAI.swift:246
        val pieces = mutableListOf<MockPiece>()   // ios: Mock/MockAI.swift:247
        for (p in jObjs(answer["pieces"]) ?: emptyList()) {   // ios: Mock/MockAI.swift:248
            val k = MockPiece.Kind.entries.firstOrNull { it.name == ((p["kind"] as? String) ?: "") } ?: continue   // ios: Mock/MockAI.swift:249
            val x = MockPiece(kind = k)   // ios: Mock/MockAI.swift:250
            if (p["hasLo"] as? Boolean == true) x.lo = MockFeed.num(p["lo"])   // ios: Mock/MockAI.swift:251
            if (p["hasHi"] as? Boolean == true) x.hi = MockFeed.num(p["hi"])   // ios: Mock/MockAI.swift:252
            if (k == MockPiece.Kind.volume || k == MockPiece.Kind.ma) x.days = min(120, max(2, (MockFeed.num(p["days"]) ?: 20.0).toInt()))   // ios: Mock/MockAI.swift:253
            x.above = (p["above"] as? Boolean) ?: true   // ios: Mock/MockAI.swift:254
            if (k == MockPiece.Kind.ma) { x.lo = null; x.hi = null }   // ios: Mock/MockAI.swift:255
            val lo = x.lo; val hi = x.hi; if (lo != null && hi != null && lo > hi) { x.lo = hi; x.hi = lo }   // ios: Mock/MockAI.swift:256
            pieces.add(x)   // ios: Mock/MockAI.swift:257
        }
        if (pieces.isEmpty()) return null   // ios: Mock/MockAI.swift:259
        val name = ((answer["name"] as? String) ?: "").trim()   // ios: Mock/MockAI.swift:260
        val sort = MockPiece.Kind.entries.firstOrNull { it.name == ((answer["sort"] as? String) ?: "") } ?: MockPiece.Kind.volume   // ios: Mock/MockAI.swift:261
        val b = MockBundle(name = if (name.isEmpty()) words.take(16) else name.take(24), pieces = pieces, sort = sort)   // ios: Mock/MockAI.swift:262
        pieces.firstOrNull { it.kind == sort }?.days?.let { d -> b.sortDays = d }   // ios: Mock/MockAI.swift:263
        return b to ((answer["note"] as? String) ?: "")   // ios: Mock/MockAI.swift:264
    }

    /** 서버 답에서 쓴 기록 */
    fun use(o: Map<String, Any?>, kind: String, summary: String): MockAIUse =   // ios: Mock/MockAI.swift:268
        MockAIUse(model = (o["model"] as? String) ?: "?", input = (MockFeed.num(o["input"]) ?: 0.0).toInt(), output = (MockFeed.num(o["output"]) ?: 0.0).toInt(),   // ios: Mock/MockAI.swift:269
            cost = MockFeed.num(o["cost"]) ?: 0.0, summary = kind + " · " + summary.take(60), cached = (o["cached"] as? Boolean) ?: false)   // ios: Mock/MockAI.swift:270

    fun wallet(o: Map<String, Any?>, at: MockDate): MockAIWallet? {   // ios: Mock/MockAI.swift:273
        val sp = jObj(o["spent"]) ?: return null   // ios: Mock/MockAI.swift:274
        return MockAIWallet(day = MockFeed.num(sp["day"]) ?: 0.0, week = MockFeed.num(sp["week"]) ?: 0.0, month = MockFeed.num(sp["month"]) ?: 0.0,   // ios: Mock/MockAI.swift:275
            at = at, key = (o["key"] as? Boolean) ?: true)   // ios: Mock/MockAI.swift:276
    }
}
