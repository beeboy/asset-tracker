package com.naeilo.app.core.calc

// 인물 자세히 보기 · "투자하는 ○○" 갈래 (사양 notes/인물-자세히보기-투자갈래-구현사양-2026-10-12.md, 상세 notes/평정-다섯-유형-상세-페이지안-2026-10-12.md).
// 대표 규칙은 Parallel.rules, 세부 규칙(버릇)은 여기. 평행 세계 인물 세계(Parallel.run ③)가 같은 규칙으로 움직인다.
// 사건 카드 그래프는 InvestEvents.kt (생성 파일을 옮긴 것). 매매 내역(종목+수량+시점)은 만들지도 보여 주지도 않는다. 유명인 이름 없음.

// ios: Data/Invest.swift:7
object Invest {
    /** 사건 카드: 강했던 때 / 약했던 때. rule·hold = 월간 S&P500 으로 굴린 선 (출발 100). fact 가 있으면 그래프 없이 숫자 카드 */
    // ios: Data/Invest.swift:9,10,11,12,13,14,15,16,17,18,19
    class Event(
        val who: String,
        val strong: Boolean,
        val title: String,
        val `when`: String,
        val from: String,
        val to: String,
        val text: String,
        val rule: DoubleArray,
        val hold: DoubleArray,
        val fact: String? = null,
    ) {
        // ios: Data/Invest.swift:20
        val id: String get() = who + title + `when`
        // ios: Data/Invest.swift:21
        val ruleRet: Double get() = (rule.lastOrNull() ?: 100.0) / 100 - 1
        // ios: Data/Invest.swift:22
        val holdRet: Double get() = (hold.lastOrNull() ?: 100.0) / 100 - 1
        // ios: Data/Invest.swift:23
        val ruleWins: Boolean get() = (rule.lastOrNull() ?: 100.0) > (hold.lastOrNull() ?: 100.0)
    }
    // ios: Data/Invest.swift:25
    fun events(who: String, strong: Boolean): List<Event> = events.filter { it.who == who && it.strong == strong }

    /** 인물 상세 맨 위 한 줄 결론 (사례 카드를 다 읽지 않아도 되게) */
    // ios: Data/Invest.swift:28
    val summary: Map<String, String> = mapOf(
        "seri" to "길게 빠지는 하락엔 덜 잃고,\n오래 오르는 상승엔 덜 벌었어요",   // ios: Data/Invest.swift:29
        "sio" to "빨리 회복하는 하락엔 강하고,\n길게 빠지는 하락엔 약했어요",   // ios: Data/Invest.swift:30
        "seonbae" to "쏠린 거품이 꺼질 땐 강하고,\n계속 오르는 종목엔 덜 벌었어요",   // ios: Data/Invest.swift:31
        "ir" to "천천히 긴 하락엔 강하고,\n급반등·횡보엔 약했어요",   // ios: Data/Invest.swift:32
        "sua" to "떨어질 때 더 사 회복 뒤 앞섰어요.\n오래 못 오르거나 적립이 끊기면 약해요",   // ios: Data/Invest.swift:33
    )
    /** 과거 사례 앞 전제 한 줄 */
    // ios: Data/Invest.swift:36
    val premise: Map<String, String> = mapOf(
        "seri" to "주식 60 · 현금 40 출발 · 세리 규칙 vs 그냥 둠",   // ios: Data/Invest.swift:37
        "sio" to "주식 80 · 현금 20 출발 · 시오 규칙 vs 그냥 둠",   // ios: Data/Invest.swift:38
        "seonbae" to "주식 60 · 현금 40 출발 · 선배 규칙 vs 그냥 둠",   // ios: Data/Invest.swift:39
        "ir" to "주식 100 출발 · 이르 규칙 vs 그냥 둠",   // ios: Data/Invest.swift:40
        "sua" to "매달 같은 금액 · 떨어진 달 더 넣기 vs 늘 같은 금액 · 넣은 돈 대비",   // ios: Data/Invest.swift:41
    )
    /** 과거 사례 (그래프 있는 것만): 연도 최신순. 유리/불리는 strong 이 아니라 끝값으로 정한다 (안정 정렬) */
    // ios: Data/Invest.swift:44,45
    fun cases(who: String): List<Event> =
        events.filter { it.who == who && it.fact == null }.sortedByDescending { year(it.`when`) }
    /** 규칙이 못 막는 하락 (숫자 카드) */
    // ios: Data/Invest.swift:48
    fun facts(who: String): List<Event> = events.filter { it.who == who && it.fact != null }
    // ios: Data/Invest.swift:49
    private fun year(s: String): Int = s.take(4).toIntOrNull() ?: 0

    /** 맨 아래 기준 줄에 덧붙일 것 (출발 비율은 전제 줄에 있으니 계산이 다른 것만) */
    // ios: Data/Invest.swift:52
    val holdNote: Map<String, String> = mapOf(
        "ir" to "200일선은 월간 10개월선으로 셈",   // ios: Data/Invest.swift:53
        "sua" to "선 100 = 본전",   // ios: Data/Invest.swift:54
    )

    /** 버릇: 세부 규칙을 그 인물 말투로 (평행 세계 인물 세계가 이대로 움직인다) */
    // ios: Data/Invest.swift:58
    val habits: Map<String, List<String>> = mapOf(
        "seri" to listOf(   // ios: Data/Invest.swift:59
            "세리는 시작한 날 기념일에만 움직여요. 그 전엔 무슨 뉴스가 와도요.",   // ios: Data/Invest.swift:60
            "처음 비율에서 ±5%p 안이면 그해에는 건드리지 않아요. 사소한 조정은 안 해요.",   // ios: Data/Invest.swift:61
            "한 종목은 40%를 넘지 않아요. 넘으면 기념일에 40%로 맞춰요.",   // ios: Data/Invest.swift:62
            "새로 넣는 돈은 모자란 쪽부터 채워요. 팔기 전에 채우기가 먼저예요.",   // ios: Data/Invest.swift:63
            "팔아야 할 때는 연 양도세 기본공제(250만원) 안쪽부터 팔고, 나머지는 다음 해로 나눠요.",   // ios: Data/Invest.swift:64
        ),
        "sio" to listOf(   // ios: Data/Invest.swift:66
            "고점보다 20% 빠지면 들고 있던 현금 절반을 넣어. 나머지 절반은 −30%까지 기다려.",   // ios: Data/Invest.swift:67
            "넣은 뒤 이전 고점을 다시 넘으면, 오른 쪽에서 덜어 현금을 20%까지 다시 채워.",   // ios: Data/Invest.swift:68
            "한 달에 매매는 두 번까지만.",   // ios: Data/Invest.swift:69
            "지수가 하루에 3% 넘게 움직인 날엔 아무것도 안 해. 다음 날 종가로 봐.",   // ios: Data/Invest.swift:70
            "현금은 단기 국채 ETF로 들고 있어.",   // ios: Data/Invest.swift:71
        ),
        "seonbae" to listOf(   // ios: Data/Invest.swift:73
            "분기에 한 번만 물어. 그 비중, 정말 네 생각이야?",   // ios: Data/Invest.swift:74
            "처음보다 10%p 넘게 커진 종목은 그만큼 덜어.",   // ios: Data/Invest.swift:75
            "10%p 넘게 작아진 종목도 그냥 두지 않아. 정말 버릴 거야? 다른 데서 덜어 채워.",   // ios: Data/Invest.swift:76
            "덜어낸 돈은 절반은 가장 작아진 종목에, 절반은 현금에.",   // ios: Data/Invest.swift:77
            "같은 종목은 분기에 한 번만. 수익 난 직후엔 더 사지 않아.",   // ios: Data/Invest.swift:78
        ),
        "ir" to listOf(   // ios: Data/Invest.swift:80
            "200일 평균선보다 3% 넘게 내려가거나 5거래일 내내 아래면, 그때 나와.",   // ios: Data/Invest.swift:81
            "다시 3% 넘게 올라오면 들어가.",   // ios: Data/Invest.swift:82
            "달리는 말에서는 안 내려. 오르는 종목은 커져도 덜지 않아.",   // ios: Data/Invest.swift:83
            "한 달에 매매는 네 번까지.",   // ios: Data/Invest.swift:84
            "전부 평균선 아래면 전부 현금이어도 괜찮아.",   // ios: Data/Invest.swift:85
        ),
        "sua" to listOf(   // ios: Data/Invest.swift:87
            "지난달 바구니가 5% 넘게 빠졌으면 1.5배를 넣어요.",   // ios: Data/Invest.swift:88
            "15% 넘게 빠졌으면 2배까지. 더는 안 넣어요.",   // ios: Data/Invest.swift:89
            "10% 넘게 오른 달엔 0.7배만 넣어요.",   // ios: Data/Invest.swift:90
            "지금 바구니 비율대로 나눠 넣어요. 빠진 종목 하나에 몰지 않아요.",   // ios: Data/Invest.swift:91
            "팔지 않아요. 못 넣은 달을 다음 달에 몰아 넣지도 않아요.",   // ios: Data/Invest.swift:92
        ),
    )
    /** 버릇 밑 한 줄 (계산에 안 넣은 것) */
    // ios: Data/Invest.swift:96
    val habitNote: Map<String, String> = mapOf(
        "seri" to "세금 순서(250만 공제)는 설명만 · 계산엔 안 넣음",   // ios: Data/Invest.swift:97
    )

    /** 반대로 간 사람: 같은 사건에서 감정대로 움직인 소설 조연 (그림 = 마네킹 샘플, 4컷 = event_<인물> 시안) */
    // ios: Data/Invest.swift:101
    data class Foil(val name: String, val line: String, val model: String, val did: String, val result: String)
    // ios: Data/Invest.swift:102
    val foils: Map<String, Foil> = mapOf(
        // ios: Data/Invest.swift:103,104
        "seri" to Foil(name = "미용사", line = "\"선택하지 않은 얼굴은 모두 잘라낼게요.\"", model = "닷컴 꼭대기 몰빵",
                       did = "2000.3 하나만 남기고 기술주에 전부", result = "나스닥 고점 회복에 15년"),
        // ios: Data/Invest.swift:105,106
        "sio" to Foil(name = "담당관", line = "\"솔직히 저도 당혹스럽습니다.\"", model = "금융위기 바닥 투매",
                      did = "2009.3 바닥에서 전부 매도", result = "10년 상승 놓침"),
        // ios: Data/Invest.swift:107,108
        "seonbae" to Foil(name = "루", line = "배송 유닛 XX-2214. 지시대로 움직인다.", model = "코로나 공포 매도",
                          did = "2020.3 묻지 않고 매도 · 6개월 늦게 복귀", result = "하락은 다 맞고 회복은 놓침"),
        // ios: Data/Invest.swift:109,110
        "ir" to Foil(name = "활동가", line = "잘린 문장을 현수막에 끝내 붙인다.", model = "밈 주식 추격",
                     did = "2021 모두가 외친 종목에 오른 뒤 올라탐", result = "대부분 고점에서 크게 하락"),
        // ios: Data/Invest.swift:111,112
        "sua" to Foil(name = "아내", line = "흙을 빚고, 묻지 않고 밥을 낸다.", model = "금리 인상기 물타기",
                      did = "2022 빠지는 한 종목에만 계속 밥을 퍼 줌", result = "한쪽 쏠림 · 회복 늦음"),
    )

    /** 이 인물의 말: 대사 데이터에서 규칙을 지킨 날 쓰는 줄 (없으면 평행 세계 말풍선) */
    // ios: Data/Invest.swift:116,117
    val quoteKey: Map<String, String> = mapOf(
        "seri" to "fb.rule.seri", "sio" to "fb.wait.sio", "seonbae" to "fb.trim.seonbae", "ir" to "fb.rule.ir", "sua" to "fb.wait.sua",
    )

    // ios: Data/Invest.swift:120
    const val disclaimer = "과거 결과 · 투자 권유 아님"

    /** 최대 낙폭 (수익률 줄, 출발 = 0). max·min 은 Swift 와 같은 비교식 */
    // ios: Data/Invest.swift:123,124,125,126
    fun maxDrawdown(r: DoubleArray): Double {
        var pk = Double.NEGATIVE_INFINITY; var m = 0.0
        for (x in r) { val v = 1 + x; pk = if (v >= pk) v else pk; if (pk > 0) { val d = v / pk - 1; m = if (d < m) d else m } }
        return m
    }
}
