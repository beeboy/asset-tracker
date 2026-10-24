package com.naeilo.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.naeilo.app.AppModel
import com.naeilo.app.core.TossDaily
import com.naeilo.app.core.USSession
import com.naeilo.app.data.Currency
import com.naeilo.app.data.Sample
import com.naeilo.app.data.Symbol
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.Market
import com.naeilo.app.net.SiteStats
import com.naeilo.app.net.YahooSample
import com.naeilo.app.ui.Card
import com.naeilo.app.ui.T
import com.naeilo.app.ui.Theme
import com.naeilo.app.ui.appFont
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 임시 진단 (내부 베타만): 아이폰 Views/PriceDiag.swift 와 같은 카드. 보유 미국 종목마다 앱이 고른 가격과 출처, 출처별 값·시각을 보인다.
// 숫자는 반올림 없이 받은 그대로. 원인을 잡으면 지운다. 키 값은 보이지 않는다.
@Composable
fun PriceDiagView(m: AppModel) {
    Card {
        T("임시 진단: 미국 종목 시세 출처", style = appFont(15f, FontWeight.Bold))
        T("지금 ${PriceDiag.et(Instant.now().epochSecond.toDouble())} · 장 닫힘 ${if (USSession.closed()) "예" else "아니오"}", style = appFont(12f), color = Theme.sub)
        for (h in m.holdings) {
            val s = Sample.symbol(h.symbol) ?: continue
            if (s.currency != Currency.usd) continue
            T(PriceDiag.line(s), style = appFont(12f).copy(fontFamily = FontFamily.Monospace), color = Theme.sub)
        }
    }
}

object PriceDiag {
    private val f = DateTimeFormatter.ofPattern("EEE MM-dd HH:mm:ss", Locale.US).withZone(ZoneId.of("America/New_York"))

    /** 미국 동부 시각 (요일 포함) */
    fun et(t: Double): String = if (t <= 0) "-" else f.format(Instant.ofEpochMilli((t * 1000).toLong())) + " ET"

    /** 반올림 없이 받은 그대로 (Double 원래 값) */
    private fun n(v: Double?): String = v?.toString() ?: "-"

    fun line(s: Symbol): String {
        val mk = Market.shared
        val q = mk.quote(s)
        val src = if (mk.isToss(s)) "toss" else if (mk.isAlpaca(s)) "alpaca" else if (mk.isLongbridge(s)) "longbridge" else if (mk.site != null) "site" else "sample/close"
        val key = YahooSample.keyFor[s.id] ?: CustomSymbols.yahoo(s)
        val hk = SiteStats.key(s.id)
        val hist = mk.tossHist?.h?.get(hk)?.let { TossDaily.Hist(dates = it.dates, closes = it.closes) }
        var r = "${s.id}  앱 ${n(q.last)} [$src]  전일 ${n(q.prevClose)}"
        val t = mk.toss?.q?.get(s.id)
        if (t != null) {
            r += "\n 토스 ${n(t.last)} @ ${et(t.at)}  주간거래 ${if (TossDaily.daytime(t.at)) "예" else "아니오"}"
            r += "\n 토스 일봉 정규장 ${n(TossDaily.reg(hist, at = t.at))}  마지막 일봉 ${hist?.dates?.lastOrNull() ?: "-"} ${n(hist?.closes?.lastOrNull())}  받은 날 ${mk.tossHist?.day?.get(hk) ?: "-"}"
        } else r += "\n 토스 없음"
        mk.alpaca?.q?.get(s.id)?.let { r += "\n Alpaca ${n(it.last)} @ ${et(it.at)}" }
        mk.longbridge?.q?.get(s.id)?.let { r += "\n Longbridge ${n(it.last)} @ ${et(it.at)}" }
        val w = mk.site?.q?.get(key)
        r += if (w != null) "\n 사이트 ${n(w.last)} 정규장 ${n(w.reg)} @ ${et(w.at)}" else "\n 사이트 없음"
        return r
    }
}
