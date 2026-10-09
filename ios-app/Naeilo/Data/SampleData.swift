import Foundation

// 시안 샘플 데이터. 시세는 어제 종가 기준 예시 값이고, 실제 앱은 PriceProvider 가 채운다.
// DRNK(드링커)는 『중첩된 현실』 속 회사를 바탕으로 한 가상 종목이다.

enum Currency { case usd, krw }

struct Symbol: Identifiable, Hashable {
    let id: String          // 티커 또는 종목 코드
    let name: String
    let market: String
    let close: Double       // 어제 종가 (시안 값)
    let currency: Currency
    let sector: String
    let search: String      // 영문 검색어
}

struct Profile {
    var mono: String
    var color: UInt32
    var fg: UInt32 = 0xFFFFFF
    var logoImage: String? = nil
    var etf = false
    var virtual = false
    var ceo: (String, String)
    var hqKey: String? = nil
    var hq: String
    var since: String
    var what: String
    var vision: String
    var extra: (String, String)? = nil
}

struct Holding: Identifiable, Hashable {
    var id: String { symbol }
    var symbol: String
    var qty: Double
    var avg: Double
}

enum Sample {
    static let fx = 1380.0            // 원/달러 (시안 가정)
    static let asOfText = "어제 종가 기준 (10월 8일)"

    static let symbols: [Symbol] = [
        .init(id: "DRNK", name: "드링커", market: "미국 주식", close: 250, currency: .usd, sector: "우주항공·궤도 통신", search: "drinker"),
        .init(id: "QQQ", name: "나스닥100 ETF", market: "미국 ETF", close: 480, currency: .usd, sector: "지수", search: "invesco nasdaq"),
        .init(id: "AAPL", name: "애플", market: "미국 주식", close: 229, currency: .usd, sector: "컴퓨터·주변기기", search: "apple"),
        .init(id: "NVDA", name: "엔비디아", market: "미국 주식", close: 182, currency: .usd, sector: "반도체", search: "nvidia"),
        .init(id: "MSFT", name: "마이크로소프트", market: "미국 주식", close: 515, currency: .usd, sector: "소프트웨어", search: "microsoft"),
        .init(id: "SPY", name: "S&P500 ETF", market: "미국 ETF", close: 660, currency: .usd, sector: "지수", search: "spdr s&p"),
        .init(id: "005930", name: "삼성전자", market: "코스피", close: 84000, currency: .krw, sector: "반도체", search: "samsung"),
        .init(id: "000660", name: "SK하이닉스", market: "코스피", close: 350000, currency: .krw, sector: "반도체", search: "sk hynix"),
        .init(id: "035720", name: "카카오", market: "코스피", close: 60000, currency: .krw, sector: "인터넷", search: "kakao"),
        .init(id: "069500", name: "KODEX 200", market: "코스피 ETF", close: 45000, currency: .krw, sector: "지수", search: "kodex"),
        .init(id: "360750", name: "TIGER 미국S&P500", market: "코스피 ETF", close: 23000, currency: .krw, sector: "지수", search: "tiger s&p"),
    ]
    static func symbol(_ id: String) -> Symbol? { symbols.first { $0.id == id } }

    // 종목 소개 (시안 예시. 실제 앱은 공시와 데이터 제공처 값으로 바꾼다). 로고는 상표 대신 글자 마크.
    static let profiles: [String: Profile] = [
        "DRNK": Profile(mono: "", color: 0x0F1424, logoImage: "drnk_logo", virtual: true,
                        ceo: ("대표", "드링커 (창업자, 본명 비공개)"), hqKey: "대표 작품", hq: "궤도 정거장 (2031년 개통)", since: "2030년",
                        what: "하늘의 깜빡이지 않는 별, 궤도 정거장을 설계하고 지었어요. 이종 연결 프로토콜 연결망과 가구 통신 계정을 깔고, 정거장을 고치고 늘려요.",
                        vision: "최초의 연결을 이어 짓는다.", extra: ("출발점", "이르의 로그 (2030년 5월)")),
        "QQQ": Profile(mono: "Q", color: 0x2F8FD8, etf: true, ceo: ("운용사", "Invesco"), hq: "미국", since: "1999년 상장",
                       what: "나스닥에 상장된 금융업 외 대형주 100개를 그대로 담는 ETF예요.", vision: "Nasdaq-100 지수", extra: ("담은 종목", "약 100개")),
        "AAPL": Profile(mono: "A", color: 0x5B6670, ceo: ("대표", "존 터너스"), hq: "미국 쿠퍼티노", since: "1976년",
                        what: "아이폰·맥·아이패드 같은 기기와 앱스토어·구독 서비스를 만들어요.", vision: "쓰기 쉬운 기기와 서비스로 사람들의 일상을 돕는다."),
        "NVDA": Profile(mono: "N", color: 0x4E9A06, ceo: ("대표", "젠슨 황"), hq: "미국 샌타클래라", since: "1993년",
                        what: "GPU와 AI 데이터센터용 칩, 그 위에서 도는 소프트웨어를 만들어요.", vision: "가속 컴퓨팅으로 AI 시대의 계산을 맡는다."),
        "MSFT": Profile(mono: "M", color: 0x2F6FD8, ceo: ("대표", "사티아 나델라"), hq: "미국 레드먼드", since: "1975년",
                        what: "윈도우·오피스, 애저 클라우드, AI 서비스를 팔아요.", vision: "모든 개인과 조직이 더 많은 것을 이루도록 돕는다."),
        "SPY": Profile(mono: "S", color: 0xC8352E, etf: true, ceo: ("운용사", "State Street"), hq: "미국", since: "1993년 상장",
                       what: "미국 대형주 500개를 시가총액 비중대로 담는 ETF예요.", vision: "S&P 500 지수", extra: ("담은 종목", "약 500개")),
        "005930": Profile(mono: "삼", color: 0x1428A0, ceo: ("대표", "전영현 · 노태문"), hq: "경기 수원", since: "1969년",
                          what: "메모리·파운드리 반도체와 스마트폰·TV·가전을 만들어요.", vision: "기술로 더 나은 세상을 만든다."),
        "000660": Profile(mono: "SK", color: 0xE8452C, ceo: ("대표", "곽노정"), hq: "경기 이천", since: "1983년",
                          what: "D램·HBM·낸드 같은 메모리 반도체를 만들어요.", vision: "AI 시대의 메모리를 앞서 만든다."),
        "035720": Profile(mono: "K", color: 0xF2C14E, fg: 0x3A1D1D, ceo: ("대표", "정신아"), hq: "제주", since: "1995년",
                          what: "카카오톡을 중심으로 광고·커머스·콘텐츠·금융 서비스를 해요.", vision: "사람과 기술로 더 나은 세상을 만든다."),
        "069500": Profile(mono: "K", color: 0x0B6B66, etf: true, ceo: ("운용사", "삼성자산운용"), hq: "한국", since: "2002년 상장",
                          what: "코스피 대형주 200개를 담는 ETF예요.", vision: "코스피 200 지수", extra: ("담은 종목", "약 200개")),
        "360750": Profile(mono: "T", color: 0xE8862A, etf: true, ceo: ("운용사", "미래에셋자산운용"), hq: "한국", since: "2020년 상장",
                          what: "미국 S&P 500 종목을 원화로 사는 국내 상장 ETF예요.", vision: "S&P 500 지수", extra: ("담은 종목", "약 500개")),
    ]

    // 시작 보유 (회복 루트 시안): DRNK 60주 $400, QQQ 10주 $500
    static let startHoldings: [Holding] = [
        .init(symbol: "DRNK", qty: 60, avg: 400),
        .init(symbol: "QQQ", qty: 10, avg: 500),
    ]

    // 3년 전망용 가정값: [현재 정세 연 기대, 과거 추세 연 성장, 연 변동성]
    static let lensParams: [String: (Double, Double, Double)] = [
        "DRNK": (0.09, 0.28, 0.42), "QQQ": (0.08, 0.16, 0.22), "AAPL": (0.08, 0.18, 0.28), "NVDA": (0.10, 0.45, 0.5),
        "MSFT": (0.08, 0.2, 0.26), "SPY": (0.07, 0.12, 0.17), "005930": (0.07, 0.05, 0.3), "000660": (0.08, 0.3, 0.45),
        "035720": (0.07, -0.1, 0.35), "069500": (0.06, 0.06, 0.2), "360750": (0.07, 0.13, 0.18),
    ]

    // 어제 하루 움직임 (홈 '어제의 움직임'과 같은 값)
    static let dayMove: [String: Double] = ["DRNK": -0.0221, "QQQ": 0.004]
}

// MARK: 쉼터 (『중첩된 현실』 외전 『이종 공명』, 이름·설정은 캐릭터 설정 스레드 기준)

struct Friend: Identifiable {
    let id: String
    let name: String
    let bio: String
    let line: String
    let kind: String
}

struct ShelterItem: Identifiable {
    let id: String
    let name: String
    let line: String
}

enum Shelter {
    static let friends: [Friend] = [
        .init(id: "seri", name: "세리", bio: "시오가 이름을 붙인 존재. 외전에서는 휴대전화 속 인공지능, 본편에서는 은색 안드로이드. 대답하기 전에 한 박자를 둔다.", line: "“거기까지만요.”", kind: "처음부터"),
        .init(id: "sio", name: "시오", bio: "숫자 하나를 매일 확인하던 회사원. 목표 100에 닿던 날, 그는 알림을 열지 않았다.", line: "“…아무도.”", kind: "회사원"),
        .init(id: "seonbae", name: "선배", bio: "가상 체험 속에서 만난, 끝까지 “왜”라고 묻던 사람. 지워져도 다시 나타난다.", line: "“기다리시면 돼요.”", kind: "노트를 든 선배"),
        .init(id: "ir", name: "이르", bio: "아르켄의 지상족. 최초의 연결을 시도하다 실패하고, 빛으로 흩어졌다.", line: "“그의 실패는 우리의 시작이었다 (동상 명판)”", kind: "아르켄의 지상족"),
        .init(id: "sua", name: "수아", bio: "먼저 오는 것과 제때 오는 것을 몸으로 느끼는 아이. 자라서 약속하지 않고 기다리는 법을 배운다.", line: "“또 올게요. 약속은 아니에요.”", kind: "기다리는 아이"),
    ]

    // 인터미션 주차별 할 일 (회복 루트)
    static let weekSteps: [(task: String, whereText: String)] = [
        ("홈 화면 위젯에서 오늘 블록 보기", "위젯"),
        ("PC 브라우저로 naeilo.com 열어 내 계획 크게 보기", "PC 웹"),
        ("앱에서 이번 주 비중 한 번 확인하기", "앱"),
        ("PC에서 1년 전망 자세히 열기", "PC 웹"),
    ]

    static let items: [ShelterItem] = [
        .init(id: "barley_tea", name: "보리차", line: "누가 시키지 않아도 놓이는 따뜻한 차 한 잔."),
        .init(id: "porch_light", name: "현관 등", line: "늦게 와도 켜져 있는 불."),
        .init(id: "wall_clock", name: "벽시계", line: "이 집에서는 아무도 시간을 맞추지 않는다."),
        .init(id: "hair_tie", name: "머리끈", line: "어중간하게 자란 머리를 묶는 고무줄."),
        .init(id: "table_chair", name: "식탁 의자와 종이", line: "식탁 한 자리와 그 위에 놓인 종이 한 장."),
        .init(id: "asym_bowl", name: "비대칭 그릇", line: "한쪽이 조금 기운 그릇. 고치지 않는다."),
        .init(id: "unfired_bowl", name: "굽지 않은 그릇", line: "아직 가마에 들어가지 않은 그릇들."),
        .init(id: "jujube_seed", name: "대추씨", line: "기다리는 동안 심은 씨앗."),
        .init(id: "elder_bead", name: "원로의 구슬", line: "먼 곳에 하나뿐인 구슬."),
        .init(id: "rice_seeds", name: "볍씨", line: "누군가 싸 준 작은 주머니."),
    ]

    // 서재 장: (이름, 같이 열리는 친구)
    static let chapters: [(title: String, friend: String)] = [
        ("프롤로그", "seri"), ("1장", "seri"), ("2장", "sio"), ("3장", "seonbae"), ("4장", "ir"), ("5장", "sua"),
    ]
    static func cover(_ i: Int) -> String {
        switch i { case 0: "art_prologue"; case 1...4: "art_ch\(i)"; default: "art_ch5_0" }
    }
}
