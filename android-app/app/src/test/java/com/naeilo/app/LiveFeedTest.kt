package com.naeilo.app

import com.naeilo.app.core.Config
import com.naeilo.app.core.FeedReq
import com.naeilo.app.core.NJson
import com.naeilo.app.core.Relay
import com.naeilo.app.net.CustomSymbols
import com.naeilo.app.net.KrDaily
import com.naeilo.app.net.MarFeed
import com.naeilo.app.net.SitePrices
import com.naeilo.app.net.SiteStats
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URL
import kotlin.test.Test

// 실제 시세 받기 (D 단계): 사이트·Yahoo·서울외국환중개에서 한 번씩 받아 읽히는지 본다. 네트워크를 쓰므로 평소에는 건너뛴다.
//   (cd android-app && ./gradlew :app:testReleaseUnitTest -PnaeiloLive=1 --tests '*LiveFeedTest*')
// 저장(Store)은 앱 안에서만 열리므로 저장하지 않는 받기·읽기 함수만 부른다. 결과는 num_parity/d/live.txt
class LiveFeedTest {
    @Test fun live() = runBlocking {
        if (System.getProperty("naeilo.live").isNullOrEmpty()) { println("live: 건너뜀 (-PnaeiloLive=1)"); return@runBlocking }
        val log = StringBuilder()
        fun note(name: String, ok: Boolean, what: String) { log.append("${if (ok) "OK " else "실패"} $name: $what\n") }
        // QuoteFeed: 사이트 quotes.json (QuoteFeed.siteFetch 와 같은 주소) + Yahoo 지금 가격 (Relay.quote)
        val q = SitePrices.get("quotes.json"); note("QuoteFeed 사이트 quotes.json", (q?.size ?: 0) > 0, "종목 ${q?.size ?: 0}개, QQQ last=${(q?.get("QQQ") as? Map<*, *>)?.get("last")}")
        for (s in listOf("QQQ", "KRW=X", "005930.KS")) { val r = Relay.quote(s); note("QuoteFeed Yahoo $s", r != null, "last=${r?.last} prev=${r?.prev}") }
        // SitePrices: Yahoo 일봉 + 사이트 index.json → prices 파일
        val d = SitePrices.daily("SPY", range = "1mo"); note("SitePrices.daily SPY 1mo", d != null, "${d?.dates?.size}일, 끝 ${d?.dates?.lastOrNull()}=${d?.closes?.lastOrNull()}")
        val idx = SitePrices.get("index.json"); val f = (idx?.get("prices") as? Map<*, *>)?.get("KRW=X") as? String
        val h = f?.let { SitePrices.get("prices/$it") }?.let { SitePrices.hist(it) }; note("SitePrices 사이트 KRW=X", h != null, "${h?.dates?.size}일, 끝 ${h?.dates?.lastOrNull()}=${h?.closes?.lastOrNull()}, updated=${idx?.get("updated")}")
        // SiteStats: 사이트 stats.json
        val st = SiteStats.download()?.second; note("SiteStats stats.json", st != null, "date=${st?.date}, 종목 ${st?.tickers?.size}, 사건 ${st?.events?.size}")
        // MarFeed: 서울외국환중개 직접 + 사이트 mar.json
        val m = MarFeed.smbs(); note("MarFeed 서울외국환중개", m != null, "${m?.date} ${m?.rate} (앞 ${m?.prev?.date} ${m?.prev?.rate})")
        val mj = MarFeed.get(FeedReq(URL(Config.data, "mar.json").toString(), timeout = 10.0)); note("MarFeed 사이트 mar.json", mj != null, "${mj?.date} ${mj?.rate}")
        // CustomSymbols.chart: 국내 종목 5일
        val c = runCatching { CustomSymbols.chart("005930.KS", range = "5d", today = true) }; note("CustomSymbols.chart 005930.KS", c.isSuccess, c.getOrNull()?.let { "last=${it.first.last} ${it.second.dates.lastOrNull()}" } ?: "${c.exceptionOrNull()}")
        // KrDaily: 사이트 kr_daily.json 은 got 칸이 없어 아이폰 Codable 처럼 읽히지 않는다 (알려진 문제, 양쪽 같음)
        val (code, data) = FeedReq(URL(Config.data, KrDaily.name).toString(), timeout = 10.0).send()
        val kd = runCatching { NJson.decodeFromString(KrDaily.File.serializer(), data.decodeToString()) }
        note("KrDaily kr_daily.json (읽기 실패가 아이폰과 같음)", code == 200 && kd.isFailure, "응답 $code, 읽기 ${if (kd.isSuccess) "성공" else "실패: got 칸 없음"}")
        val out = File(System.getProperty("user.home"), "Library/Caches/naeilo-android/num_parity/d/live.txt"); out.parentFile?.mkdirs(); out.writeText(log.toString())
        print(log)
    }
}
