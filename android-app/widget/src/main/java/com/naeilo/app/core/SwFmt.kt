package com.naeilo.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Swift 숫자 글자(.formatted()) 자리. ICU 처럼 가장 짧은 십진 표기(2.675)를 먼저 만들고 그 십진수에서 짝수 쪽으로 반올림한다
// (Java 가 double 그대로 반올림하면 2.675 → 2.67 이 되어 아이폰 2.68 과 달라진다). 숫자 맞추기 F 로 Swift 와 같은지 본다

private val sym = DecimalFormatSymbols(Locale.ROOT)
private val cache = ThreadLocal.withInitial { HashMap<String, DecimalFormat>() }
private fun fmt(p: String): DecimalFormat = cache.get()!!.getOrPut(p) { DecimalFormat(p, sym).apply { roundingMode = RoundingMode.HALF_EVEN } }
private fun Double.decimal(): Any = if (isFinite()) BigDecimal(toString()) else this

/** x.formatted(): 세 자리 쉼표, 소수 6자리까지 */
fun Double.swFmt(): String = fmt("#,##0.######").format(decimal())
/** x.formatted(.number.grouping(.never)) */
fun Double.swFmtNoGroup(): String = fmt("0.######").format(decimal())
/** x.formatted(.number.precision(.fractionLength(n))): 세 자리 쉼표, 소수 n 자리 */
fun Double.swFmtFrac(n: Int): String = fmt("#,##0" + (if (n > 0) "." + "0".repeat(n) else "")).format(decimal())
/** x.formatted(.number.precision(.fractionLength(a...b))): 소수 a~b 자리 */
fun Double.swFmtFrac(a: Int, b: Int): String = fmt("#,##0" + (if (b > 0) "." + "0".repeat(a) + "#".repeat(b - a) else "")).format(decimal())

/** String(format: "%.1f", x) 자리: 무한·숫자 아님은 C 처럼 inf·-inf·nan 으로 (Java 는 Infinity·NaN) */
fun cfmt(pattern: String, x: Double): String = cformat(pattern, x)

/** String(format: "%.nf", v) 한 칸. Java 는 짧은 십진 표기에서 위로 반올림해(1.25 → 1.3, 2.675 → 2.68) C(1.2, 2.67)와 다르다.
 *  C 처럼 double 값 그대로 짝수 쪽 반올림 (음수는 0 이 되어도 − 부호, C 와 같게 "-0.0") */
fun cfix(v: Double, n: Int): String {
    if (v.isNaN()) return "nan"
    if (v.isInfinite()) return if (v > 0) "inf" else "-inf"
    val s = BigDecimal(kotlin.math.abs(v)).setScale(n, RoundingMode.HALF_EVEN).toPlainString()
    return if (v < 0 || (v == 0.0 && 1.0 / v < 0)) "-$s" else s
}
/** "%g" 한 칸: C 와 같게 유효 6자리, 끝 0 지움, 지수는 두 자리 이상 */
fun cg(v: Double): String {
    if (v.isNaN()) return "nan"
    if (v.isInfinite()) return if (v > 0) "inf" else "-inf"
    if (v == 0.0) return if (1.0 / v < 0) "-0" else "0"
    val bd = BigDecimal(v).round(java.math.MathContext(6, RoundingMode.HALF_EVEN))
    val e = bd.precision() - bd.scale() - 1
    if (e < -4 || e >= 6) return bd.movePointLeft(e).stripTrailingZeros().toPlainString() + "e" + (if (e < 0) "-" else "+") + String.format(Locale.US, "%02d", kotlin.math.abs(e))
    return bd.stripTrailingZeros().toPlainString()
}
private val SPEC = Regex("%([-#+ 0,(]*)(\\d+)?(?:\\.(\\d+))?([a-zA-Z%])")
/** String(format:) 자리. 꾸밈 없는 %f·%.nf·%g 실수는 C 와 같게(cfix·cg), 나머지(%d·%02x·꾸밈 붙은 것)는 Java 그대로 */
fun cformat(fmt: String, vararg a: Any?): String {
    var i = 0
    return SPEC.replace(fmt) { m ->
        val (flags, width, prec, conv) = m.destructured
        when {
            conv == "%" -> "%"
            conv == "n" -> "\n"
            else -> {
                val x = a[i++]
                val plain = flags.isEmpty() && width.isEmpty()
                if (x is Double && plain && conv == "f") cfix(x, prec.ifEmpty { "6" }.toInt())
                else if (x is Double && plain && conv == "g" && prec.isEmpty()) cg(x)
                else String.format(Locale.US, m.value, x)
            }
        }
    }
}
