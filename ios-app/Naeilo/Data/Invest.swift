import Foundation

// 인물 자세히 보기 · "투자하는 ○○" 갈래 (사양 notes/인물-자세히보기-투자갈래-구현사양-2026-10-12.md, 상세 notes/평정-다섯-유형-상세-페이지안-2026-10-12.md).
// 대표 규칙은 Parallel.rules, 세부 규칙(버릇)은 여기. 평행 세계 인물 세계(Parallel.run ③)가 같은 규칙으로 움직인다.
// 사건 카드 그래프는 InvestEvents.swift (생성 파일). 매매 내역(종목+수량+시점)은 만들지도 보여 주지도 않는다. 유명인 이름 없음.

enum Invest {
    /// 사건 카드: 강했던 때 / 약했던 때. rule·hold = 월간 S&P500 으로 굴린 선 (출발 100). fact 가 있으면 그래프 없이 숫자 카드
    struct Event: Identifiable {
        let who: String
        let strong: Bool
        let title: String
        let when: String
        let from: String
        let to: String
        let text: String
        let rule: [Double]
        let hold: [Double]
        var fact: String? = nil
        var id: String { who + title + when }
        var ruleRet: Double { (rule.last ?? 100) / 100 - 1 }
        var holdRet: Double { (hold.last ?? 100) / 100 - 1 }
        var ruleWins: Bool { (rule.last ?? 100) > (hold.last ?? 100) }
    }
    static func events(_ who: String, strong: Bool) -> [Event] { events.filter { $0.who == who && $0.strong == strong } }

    /// 인물 상세 맨 위 한 줄 결론 (사례 카드를 다 읽지 않아도 되게)
    static let summary: [String: String] = [
        "seri": String(localized: "길게 빠지는 하락엔 덜 잃고,\n오래 오르는 상승엔 덜 벌었어요"),
        "sio": String(localized: "빨리 회복하는 하락엔 강하고,\n길게 빠지는 하락엔 약했어요"),
        "seonbae": String(localized: "쏠린 거품이 꺼질 땐 강하고,\n계속 오르는 종목엔 덜 벌었어요"),
        "ir": String(localized: "천천히 긴 하락엔 강하고,\n급반등·횡보엔 약했어요"),
        "sua": String(localized: "떨어질 때 더 사 회복 뒤 앞섰어요.\n오래 못 오르거나 적립이 끊기면 약해요"),
    ]
    /// 과거 사례 앞 전제 한 줄
    static let premise: [String: String] = [
        "seri": String(localized: "주식 60 · 현금 40 출발 · 세리 규칙 vs 그냥 둠"),
        "sio": String(localized: "주식 80 · 현금 20 출발 · 시오 규칙 vs 그냥 둠"),
        "seonbae": String(localized: "주식 60 · 현금 40 출발 · 선배 규칙 vs 그냥 둠"),
        "ir": String(localized: "주식 100 출발 · 이르 규칙 vs 그냥 둠"),
        "sua": String(localized: "매달 같은 금액 · 떨어진 달 더 넣기 vs 늘 같은 금액 · 넣은 돈 대비"),
    ]
    /// 과거 사례 (그래프 있는 것만): 연도 최신순. 유리/불리는 strong 이 아니라 끝값으로 정한다
    static func cases(_ who: String) -> [Event] {
        events.filter { $0.who == who && $0.fact == nil }.sorted { year($0.when) > year($1.when) }
    }
    /// 규칙이 못 막는 하락 (숫자 카드)
    static func facts(_ who: String) -> [Event] { events.filter { $0.who == who && $0.fact != nil } }
    private static func year(_ s: String) -> Int { Int(s.prefix(4)) ?? 0 }

    /// 맨 아래 기준 줄에 덧붙일 것 (출발 비율은 전제 줄에 있으니 계산이 다른 것만)
    static let holdNote: [String: String] = [
        "ir": String(localized: "200일선은 월간 10개월선으로 셈"),
        "sua": String(localized: "선 100 = 본전"),
    ]

    /// 버릇: 세부 규칙을 그 인물 말투로 (평행 세계 인물 세계가 이대로 움직인다)
    static let habits: [String: [String]] = [
        "seri": [
            String(localized: "세리는 시작한 날 기념일에만 움직여요. 그 전엔 무슨 뉴스가 와도요."),
            String(localized: "처음 비율에서 ±5%p 안이면 그해에는 건드리지 않아요. 사소한 조정은 안 해요."),
            String(localized: "한 종목은 40%를 넘지 않아요. 넘으면 기념일에 40%로 맞춰요."),
            String(localized: "새로 넣는 돈은 모자란 쪽부터 채워요. 팔기 전에 채우기가 먼저예요."),
            String(localized: "팔아야 할 때는 연 양도세 기본공제(250만원) 안쪽부터 팔고, 나머지는 다음 해로 나눠요."),
        ],
        "sio": [
            String(localized: "고점보다 20% 빠지면 들고 있던 현금 절반을 넣어. 나머지 절반은 −30%까지 기다려."),
            String(localized: "넣은 뒤 이전 고점을 다시 넘으면, 오른 쪽에서 덜어 현금을 20%까지 다시 채워."),
            String(localized: "한 달에 매매는 두 번까지만."),
            String(localized: "지수가 하루에 3% 넘게 움직인 날엔 아무것도 안 해. 다음 날 종가로 봐."),
            String(localized: "현금은 단기 국채 ETF로 들고 있어."),
        ],
        "seonbae": [
            String(localized: "분기에 한 번만 물어. 그 비중, 정말 네 생각이야?"),
            String(localized: "처음보다 10%p 넘게 커진 종목은 그만큼 덜어."),
            String(localized: "10%p 넘게 작아진 종목도 그냥 두지 않아. 정말 버릴 거야? 다른 데서 덜어 채워."),
            String(localized: "덜어낸 돈은 절반은 가장 작아진 종목에, 절반은 현금에."),
            String(localized: "같은 종목은 분기에 한 번만. 수익 난 직후엔 더 사지 않아."),
        ],
        "ir": [
            String(localized: "200일 평균선보다 3% 넘게 내려가거나 5거래일 내내 아래면, 그때 나와."),
            String(localized: "다시 3% 넘게 올라오면 들어가."),
            String(localized: "달리는 말에서는 안 내려. 오르는 종목은 커져도 덜지 않아."),
            String(localized: "한 달에 매매는 네 번까지."),
            String(localized: "전부 평균선 아래면 전부 현금이어도 괜찮아."),
        ],
        "sua": [
            String(localized: "지난달 바구니가 5% 넘게 빠졌으면 1.5배를 넣어요."),
            String(localized: "15% 넘게 빠졌으면 2배까지. 더는 안 넣어요."),
            String(localized: "10% 넘게 오른 달엔 0.7배만 넣어요."),
            String(localized: "지금 바구니 비율대로 나눠 넣어요. 빠진 종목 하나에 몰지 않아요."),
            String(localized: "팔지 않아요. 못 넣은 달을 다음 달에 몰아 넣지도 않아요."),
        ],
    ]
    /// 버릇 밑 한 줄 (계산에 안 넣은 것)
    static let habitNote: [String: String] = [
        "seri": String(localized: "세금 순서(250만 공제)는 설명만 · 계산엔 안 넣음"),
    ]

    /// 반대로 간 사람: 같은 사건에서 감정대로 움직인 소설 조연 (그림 = 마네킹 샘플, 4컷 = event_<인물> 시안)
    struct Foil { let name: String; let line: String; let model: String; let did: String; let result: String }
    static let foils: [String: Foil] = [
        "seri": Foil(name: String(localized: "미용사"), line: String(localized: "\"선택하지 않은 얼굴은 모두 잘라낼게요.\""), model: String(localized: "닷컴 꼭대기 몰빵"),
                     did: String(localized: "2000.3 하나만 남기고 기술주에 전부"), result: String(localized: "나스닥 고점 회복에 15년")),
        "sio": Foil(name: String(localized: "담당관"), line: String(localized: "\"솔직히 저도 당혹스럽습니다.\""), model: String(localized: "금융위기 바닥 투매"),
                    did: String(localized: "2009.3 바닥에서 전부 매도"), result: String(localized: "10년 상승 놓침")),
        "seonbae": Foil(name: String(localized: "루"), line: String(localized: "배송 유닛 XX-2214. 지시대로 움직인다."), model: String(localized: "코로나 공포 매도"),
                        did: String(localized: "2020.3 묻지 않고 매도 · 6개월 늦게 복귀"), result: String(localized: "하락은 다 맞고 회복은 놓침")),
        "ir": Foil(name: String(localized: "활동가"), line: String(localized: "잘린 문장을 현수막에 끝내 붙인다."), model: String(localized: "밈 주식 추격"),
                   did: String(localized: "2021 모두가 외친 종목에 오른 뒤 올라탐"), result: String(localized: "대부분 고점에서 크게 하락")),
        "sua": Foil(name: String(localized: "아내"), line: String(localized: "흙을 빚고, 묻지 않고 밥을 낸다."), model: String(localized: "금리 인상기 물타기"),
                    did: String(localized: "2022 빠지는 한 종목에만 계속 밥을 퍼 줌"), result: String(localized: "한쪽 쏠림 · 회복 늦음")),
    ]

    /// 이 인물의 말: 대사 데이터에서 규칙을 지킨 날 쓰는 줄 (없으면 평행 세계 말풍선)
    static let quoteKey: [String: String] = [
        "seri": "fb.rule.seri", "sio": "fb.wait.sio", "seonbae": "fb.trim.seonbae", "ir": "fb.rule.ir", "sua": "fb.wait.sua",
    ]

    static let disclaimer = String(localized: "과거 결과 · 투자 권유 아님")

    /// 최대 낙폭 (수익률 줄, 출발 = 0)
    static func maxDrawdown(_ r: [Double]) -> Double {
        var pk = -Double.infinity, m = 0.0
        for x in r { let v = 1 + x; pk = max(pk, v); if pk > 0 { m = min(m, v / pk - 1) } }
        return m
    }
}
