package com.naeilo.app

import com.naeilo.app.core.NJson
import com.naeilo.app.net.jsonToPlain
import com.naeilo.app.net.plainToJson
import com.naeilo.app.ui.analysis.AnalysisHub
import com.naeilo.app.ui.analysis.ForecastView
import com.naeilo.app.ui.analysis.ScenarioChart
import com.naeilo.app.ui.analysis.manS
import com.naeilo.app.ui.analysis.won1
import com.naeilo.app.ui.analysis.wonRead
import java.io.File
import kotlin.math.abs
import kotlin.test.Test

// 숫자 맞추기 G (분석): tools/num_parity/g/gsrc.py 가 꺼낸 Swift 와 같은 입력으로 kotlin.json 을 쓴다.
// 전망 금액 글·햇달·빈도·그래프 눈금, 읽기 쉬운 금액. 비교는 parity.py check (차이 0)
class GParityTest {
    private val dir = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/g")

    @Suppress("UNCHECKED_CAST")
    @Test fun gStage() {
        val inF = dir.resolve("input.json")
        if (!inF.exists()) { println("num_parity g: 입력 없음 (parity.py gen 먼저)"); return }
        val lz = AppModel::class.java.getDeclaredField("en\$delegate").apply { isAccessible = true }.get(null)
        lz.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(lz, false)
        val inp = jsonToPlain(NJson.parseToJsonElement(inF.readText())) as Map<String, Any?>
        val won = inp["won"] as List<Double>; val ps = inp["p"] as List<Double>
        val out = LinkedHashMap<String, Any?>()
        out["amt"] = won.map { ForecastView.amt(it) }
        out["short"] = listOf(Lens.base, Lens.mine, Lens.smooth).map { ForecastView.short(it) }
        out["ym"] = (inp["dates"] as List<String>).map { val x = ForecastView.ym(it); listOf(x.first, x.second) }
        out["freq"] = ps.map { AnalysisHub.freq(it) }
        out["step"] = won.map { ScenarioChart.step(abs(it)) }
        out["axis"] = won.map { ScenarioChart.axis(it) }
        out["manS"] = won.map { AppModel.manS(it) }
        out["won1"] = won.map { AppModel.won1(it) }
        out["wonRead"] = won.map { AppModel.wonRead(it) }
        dir.resolve("kotlin.json").writeText(plainToJson(out).toString())
        println("num_parity g: 금액 ${won.size}")
    }
}
