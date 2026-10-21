package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.core.calc.Route
import com.naeilo.app.core.calc.eta
import com.naeilo.app.core.calc.wonK
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import com.naeilo.app.ui.Pose
import com.naeilo.app.ui.Voice
import com.naeilo.app.ui.settings.TaxCalc
import com.naeilo.app.ui.start.StartForecast
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test

// 숫자 맞추기 E (홈·설정): tools/num_parity/e/esrc.py 가 꺼낸 Swift 와 같은 입력·같은 차례로 돌려 kotlin.json 을 쓴다.
// 첫 질문 예보 카드(값·부채꼴 시작점·큰 글), 인물 말투, 세금 규칙, 홈 '%.1f' 글. 비교는 parity.py check (차이 0)
class EParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/e")

    @Suppress("UNCHECKED_CAST")
    @Test fun eStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity e: 입력 없음 (parity.py gen 먼저)"); return }
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        fun nums(k: String) = (inp[k] as List<*>).map { it as Double }
        fun strs(k: String) = (inp[k] as List<*>).map { it as String }
        // 앱 언어는 한국어로 (AppModel.en 은 저장소에 언어를 적느라 Context 가 필요하다: 시험에선 값만 넣는다)
        val lz = AppModel::class.java.getDeclaredField("en\$delegate").apply { isAccessible = true }.get(null)
        lz.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(lz, false)
        val out = LinkedHashMap<String, Any?>()
        val ts = (0..30).map { if (it == 30) 3.0 else it * 0.1 }
        out["ts"] = ts
        out["value"] = buildList { for (v in nums("v0s")) for (m in nums("ms")) for (t in ts) for (z in nums("zs")) add(StartForecast.value(v, m, t, z)) }
        val sizes = (inp["sizes"] as List<*>).map { val a = it as List<*>; Pair(a[0] as Double, a[1] as Double) }
        out["ex"] = listOf(null, Route.recover, Route.plus, Route.novice).map { r ->
            val e = StartForecast.ex(r)
            mapOf("v0" to e.v0, "monthly" to e.monthly, "line" to e.line, "lineName" to e.lineName, "big" to e.big, "sub" to e.sub,
                  "fanStart" to sizes.map { s -> val p = StartForecast.fanStartD(e, s.first, s.second, 1.0); listOf(p.first, p.second) })
        }
        val poses = listOf(Pose.stand, Pose.spread, Pose.point, Pose.wave, Pose.think, Pose.cheer, Pose.side, Pose.back, Pose.sideL)
        out["voice"] = strs("says").map { t -> strs("friends").map { f -> poses.map { Voice.say(t, f, it) } } }
        out["tax"] = nums("gains").map { G -> val tax = TaxCalc.tax(G); mapOf("tax" to tax, "won0" to TaxCalc.won0(tax), "G" to TaxCalc.won0(G)) }
        out["need"] = nums("fracs").map { x -> listOf("+" + String.format(Locale.US, "%.1f", x * 100), String.format(Locale.US, "%.1f%%", abs(x) * 100)) }
        out["etc"] = nums("fracs").map { x -> listOf(AppModel.pct(x), AppModel.wonK(x * 1e4), AppModel.eta(x * 120)) }
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity e: 값 ${(out["value"] as List<*>).size}")
    }
}
