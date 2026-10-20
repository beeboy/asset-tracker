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
fun cfmt(pattern: String, x: Double): String =
    if (x.isNaN()) "nan" else if (x.isInfinite()) (if (x > 0) "inf" else "-inf") else String.format(Locale.US, pattern, x)
