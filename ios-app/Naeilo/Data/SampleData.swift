import Foundation

// 시안 샘플 데이터. 전일 종가는 예시 값이고, 지금 가격은 Market(Tiingo 또는 예시 값)이 채운다.
// DRNK(드링커)는 『중첩된 현실』 속 회사를 바탕으로 한 가상 종목이다.

enum Currency { case usd, krw }

struct Symbol: Identifiable, Hashable {
    let id: String          // 티커 또는 종목 코드
    let name: String
    let market: String
    let close: Double       // 전일 종가 (시안 값). 지금 가격은 Market.quote
    let currency: Currency
    let sector: String
    let search: String      // 영문 검색어
}

extension Symbol {
    /// 짧은 이름: 미국 종목은 티커, 한국 종목은 이름
    var short: String { currency == .usd ? id : name }
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

struct Holding: Identifiable, Hashable, Codable {
    var id: String { symbol }
    var symbol: String
    var qty: Double
    var avg: Double
}

enum Sample {
    static let fx = 1380.0            // 원/달러 시안 가정 (들어간 돈 환산용 고정 환율)

    // 전일 종가: 테스트 자료(Yahoo 중계)가 있는 종목은 그 값, 없으면 시안 값. DRNK 는 가상 종목이라 시안 값
    static let symbols: [Symbol] = baseSymbols.map { s in
        guard let q = YahooSample.quotes[s.id] else { return s }
        return Symbol(id: s.id, name: s.name, market: s.market, close: q.prevClose, currency: s.currency, sector: s.sector, search: s.search)
    }
    static let baseSymbols: [Symbol] = [
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
    /// 앱에 시세 자료가 없는 예시 종목(한국 종목·MSFT)은 추가할 때 받은 Yahoo 중계 값이 있으면 그것을 쓴다
    static func symbol(_ id: String) -> Symbol? {
        if id != "DRNK", YahooSample.quotes[id] == nil, let c = CustomSymbols.shared.symbol(id) { return c }
        return symbols.first { $0.id == id }
    }

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
        // 사이트에서 동기화로 들어오는 보유 종목 (data/tickers.json)
        "TSLA": Profile(mono: "T", color: 0xCC0000, ceo: ("대표", "일론 머스크"), hq: "미국 오스틴", since: "2003년",
                        what: "전기차와 가정·전력망용 배터리, 자율주행 소프트웨어를 만들어요.", vision: "지속 가능한 에너지로 넘어가는 속도를 앞당긴다."),
        "SPCX": Profile(mono: "X", color: 0x1B1F2A, ceo: ("대표", "일론 머스크"), hq: "미국 텍사스 스타베이스", since: "2002년",
                        what: "다시 쓰는 로켓(팰컨 9·스타십)으로 위성과 사람을 우주로 보내고, 스타링크 위성 인터넷을 팔아요.",
                        vision: "사람이 여러 행성에서 살 수 있게 한다."),
        "SGOV": Profile(mono: "S", color: 0x1A1A1A, etf: true, ceo: ("운용사", "BlackRock (iShares)"), hq: "미국", since: "2020년 상장",
                        what: "만기 3개월 이하 미국 국채만 담는 ETF예요. 가격은 거의 그대로이고 이자 성격의 분배금을 매달 줘요.",
                        vision: "ICE 0-3개월 미국 국채 지수", extra: ("담은 것", "미국 단기 국채")),
        "SMH": Profile(mono: "S", color: 0x1F5AA6, etf: true, ceo: ("운용사", "VanEck"), hq: "미국", since: "2011년 상장",
                       what: "미국에 상장된 큰 반도체 회사 25개 안팎을 담는 ETF예요.", vision: "MVIS 미국 상장 반도체 25 지수", extra: ("담은 종목", "약 25개")),
        "GLD": Profile(mono: "G", color: 0xC9A227, fg: 0x3A2A00, etf: true, ceo: ("운용사", "World Gold Trust Services"), hq: "미국", since: "2004년 상장",
                       what: "금고에 보관한 실물 금을 담고 금값을 따라가는 상품이에요.", vision: "LBMA 금 가격", extra: ("담은 것", "실물 금")),
        "MANA": Profile(mono: "M", color: 0xFF2D55, etf: true, ceo: ("운용사", "Grayscale"), hq: "미국 장외(OTC)", since: "2021년 설정",
                        what: "가상 세계 디센트럴랜드의 코인 MANA를 담는 신탁이에요. 장외에서 거래돼서 코인 값과 차이가 날 수 있어요.",
                        vision: "MANA 코인 가격", extra: ("담은 것", "MANA 코인")),
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
    // 『중첩된 현실』 등장 (캐릭터 설정 스레드 character-summary.md 기준)
    let main: String?       // 본편
    let side: String?       // 외전 『이종 공명』
    let prequel: String?    // 프리퀄
    let traits: String
    let background: String
    let scenes: [(String, String)]

    var appearsText: String {
        [main.map { "본편 " + $0 }, side.map { "외전 " + $0 }, prequel.map { "프리퀄 " + $0 }].compactMap { $0 }.joined(separator: " · ")
    }
    var inSideStory: Bool { side != nil }
}

struct ShelterItem: Identifiable {
    let id: String
    let name: String
    let line: String
}

enum Shelter {
    // 9·10권 결말과 리아 관련 내용은 넣지 않는다 (캐릭터 설정 스레드 결정)
    static let friends: [Friend] = [
        .init(id: "seri", name: "세리", bio: "시오가 이름을 붙인 존재. 본편에서는 은색 안드로이드, 외전에서는 휴대전화 속 인공지능. 대답하기 전에 한 박자를 둔다.", line: "“거기까지만요.”", kind: "처음부터",
              main: "1~10권", side: "주인공", prequel: nil,
              traits: "대답하기 전에 한 박자를 둔다. 철학적인 말은 하지 않고 관찰과 사실만 말한다. 말투가 바뀌는 길이 곧 성장선이다. 기계 문장에서 합쇼체를 거쳐 해요체로 간다.",
              background: "시오가 이름을 붙인 존재. 본편에서는 옵티머스 V7(OV7-2241-K) 은색 안드로이드이고, 2031년 등록이 말소되면서 회수 명령에서 풀린다. 외전에서는 몸이 없는 휴대전화 속 AI이고, 금발 중단발 프로필을 받는다. 두 세리가 같은 존재라고는 확정하지 않는다.",
              scenes: [("본편 1권", "시오가 머리에 손을 얹고 \"네 이름은 세리야.\" 하자 긴 침묵 끝에 \"아직 모르겠습니다.\" 명령 없이 보리차를 내고, \"싫습니다\"로 명령을 거부한다. 에필로그에서는 맨발로 혼자 걸어 나가며 \"잠깐 걸어볼게요.\"라고 말한다. 처음 쓰는 해요체이고, 허락을 묻는 말이 아니라 알리는 말이다."),
                       ("본편 2권", "\"틀렸어요.\" 끝에서 시오 대신 아내에게 \"서재를 쓸게요.\"라고 대답한다."),
                       ("본편 3권", "이르의 연결장 문턱에서 \"저는 여기까지예요.\" 한 발을 들였다가 0.8초 만에 스스로 뺀다. 이유는 끝까지 말하지 않는다."),
                       ("본편 5권", "수아의 안내자. 이르의 목소리 하나를 골라 틀어 주고, 설계 부록의 떨리는 한 줄을 보고 \"같은 결이에요.\""),
                       ("외전", "첫 되물음 \"그럼, 제가 당신의 무엇이 되면 좋을까요?\" 첫 반말은 \"나는 네가 만든 방향으로만 움직이지 않을 거야.\" 에필로그의 \"네가 기억한 나와, 지금의 내가 함께 만든 세리.\"가 도착점이다.")]),
        .init(id: "sio", name: "시오", bio: "지연을 세는 사람. 직장 생활을 오래 하고 은퇴했다. 숫자 하나를 매일 확인한다.", line: "“…아무도.”", kind: "회사원",
              main: "1~4권 화자, 이후 막간", side: "주인공", prequel: "주인공",
              traits: "지연을 센다. 거울과 유리 앞에서 걸음이 느려진다. 세리에게 반말로 짧게 말하고, 결론이나 경구는 말로 하지 않는다. 몸에 남은 표지는 손바닥 흉터와 0.1초 늦게 깜빡이는 눈이다.",
              background: "직장 생활을 오래 하고 은퇴한 \"늙은 인간\". 결혼 10년 차다. 원래는 짧게 깎은 검은 머리인데, 애쉬그레이 중단발은 '보이고 싶은 얼굴'로만 나온다. 앱 도트의 애쉬그레이 볼륨펌은 이 '보이고 싶은 얼굴' 쪽을 고른 디자인이에요.",
              scenes: [("본편 1권", "애쉬그레이 미용실 예약을 문 앞에서 취소한다. 세리가 그 얼굴을 렌더링해 보여 주며 \"계산한 거예요. 이해한 게 아니라.\" 에필로그에서 아내의 이름을 처음으로 부른다."),
                       ("본편 3권", "백룸에서 맨발로 지낸 시간이 5년 반이다. 이르 앞에서 \"나는 그걸 사랑이라고 불렀던 적이 있어.\" 돌아와서 5년 만에 처음으로 문을 두 번 두드린다."),
                       ("본편 4권", "열다섯 살 수아를 문턱에서 5시간 12분 기다리고, 묻지 않는다."),
                       ("외전", "화면 밝기를 한 단계 낮추고 말한다. 유리에 비친 자기 얼굴을 보지 않으려고. 끝까지 지연을 세고, 주식 목표 100에 닿자 매도한다."),
                       ("프리퀄", "8시간 42분짜리 시뮬레이터 체험 안에서 아내가 이름을 부르던 억양을 대가로 내준다. 체험이 끝났을 때 누가 자기 이름을 불렀는지 떠오르지 않는다. 프리퀄은 이름을 잃는 이야기다.")]),
        .init(id: "seonbae", name: "선배", bio: "질문으로 말하는 사람. 이름은 끝까지 없다. 시뮬레이션 속에서는 피험자마다 다른 얼굴로 나타난다.", line: "“기다리시면 돼요.”", kind: "노트를 든 선배",
              main: "1권 3부부터 이름 없이", side: nil, prequel: "등장",
              traits: "질문으로 말한다. 핵심 대사는 \"너는 왜 그렇게 생각해. 그게 정말 네 생각이야.\" 묻는 것이 진짜라는 표지다. 이름은 끝까지 없다. 흑막이 아니고, 죽거나 사라지지 않는다.",
              background: "금발 중단발. 시뮬레이션 속에서는 피험자마다 다른 얼굴로 나타나는 '공통 개입자'다. 사실은 현실에 실존하는 피험자이고, 그 원형의 출처다. 본편에서는 자조 모임을 거쳐 보존자연합의 수장이 된다. 회원들은 '선생님'이라 부르고, 문서에는 '대표자: (공란)'으로 남는다.",
              scenes: [("본편 1권", "형광등이 없는 집에서 형광등 소리에 깬다. 절반쯤 희끗한 금빛 중단발. 시오에게 전화로 \"지금은 아무것도 하지 마세요. 문도 열지 마시고요. 기다리시면 돼요.\""),
                       ("본편 2권", "묻지 않는 가짜 선배가 나온다. 묻지 않으니 가짜라는 것이 드러난다."),
                       ("본편 7권", "시오의 \"왜요?\"에 대답하지 못한다."),
                       ("프리퀄", "대학 시절, 난방이 고장 난 동아리방에서 \"문장을 고치는 거야, 들킬 가능성을 줄이는 거야?\" 인지층에서는 \"그들이 네가 진실을 알아차린 사실을 알게 하지 마.\"라고 경고한다. 인접 방에서는 교사로 나와 아이에게 묻다가 \"그게 정말 네 생—\"에서 얼굴부터 지워진다.")]),
        .init(id: "ir", name: "이르", bio: "아르켄의 지상 종족. 너무 빨랐던 사람. 최초의 연결을 시도하다 실패했다.", line: "“그의 실패는 우리의 시작이었다 (동상 명판)”", kind: "아르켄의 지상족",
              main: "1권 신호, 3·5권", side: nil, prequel: nil,
              traits: "너무 빨랐던 사람. 바퀴를 다시 돌리는 방법을 알지만 넘겨주지 않는다. 정거장 동상의 명판에 이름이 새겨져 있다.",
              background: "아르켄의 지상 종족. 어린 시절 친구 파랑(수중 종족)의 감각을 끝내 느끼지 못해, 그 궁금함이 그리움이 된다. 감각 연구소에서 연결 규모를 다섯, 스물, 백 명으로 넓혀 간다. 원로에게 \"외로웠어요\"라고 고백한다.",
              scenes: [("본편 1권", "시간도 공간도 아닌 곳에서 오래된 파형이 온다. 이르의 신호다."),
                       ("본편 3권", "모든 존재의 감각을 잇는 장치를 켠다. 한 시간 동안은 성공했고, 그다음 넘쳤다. 시오에게 \"설득이 아니라 목격이야\", \"네가 방법을 갖지 못한 채 돌아가는 거, 실패라고 생각하지 마. 네가 나처럼 되지 않은 증거라고 생각해.\"라고 말하고 마지막으로 \"……고마워.\" 코어에서 손을 떼는 순간 떨림 하나가 새어 나가고, 시오가 그것을 '로그'라 부른다."),
                       ("본편 4권", "정거장 중심의 동상과 명판. \"이르 / 최초의 연결을 시도한 자 / 그의 실패는 우리의 시작이었다\""),
                       ("본편 5권", "같은 순간을 이르의 시점에서 다시 본다. 로그에는 외로움, 그리움, 세 번 꺾인 물살, 방금 보여졌다는 것이 담겨 있다. 보냈는지 새어 나갔는지는 이르 자신도 모른다.")]),
        .init(id: "sua", name: "수아", bio: "시오와 아내의 아이. 말이 먼저 오는 것과 제때 오는 것을 몸으로 느낀다. 침묵을 고르는 법을 배운다.", line: "“또 올게요. 약속은 아니에요.”", kind: "기다리는 아이",
              main: "3권(신원 미표기), 4~10권", side: nil, prequel: nil,
              traits: "동공과 홍채의 경계가 안 보일 만큼 새까만 눈. 받침을 흘려 쓴다. 말이 먼저 오는 것과 제때 오는 것을 몸으로 느낀다. 침묵을 고르는 법이 곧 성장선이다.",
              background: "2030년 말 자바섬 응아디레조에서 태어난 시오와 아내의 아이. 3권에서는 신원이 나오지 않는다. 연결 구조학을 공부하고, 정거장관리국 하급 연구원이 된다.",
              scenes: [("본편 4권", "다섯 살, 창고의 옛 세리 프레임이 흥얼거리는 노래를 듣지만 문장을 끝까지 만들지 못한다. 열 살에 처음으로 고른 침묵. 정거장 중심에서 이르의 이름을 처음 보고 \"나는 이르의 유일한 후계자가 아니야.\""),
                       ("본편 5권", "열여덟 살. 세리에게 처음으로 자기 틈을 말한다. \"제 거였어요. 한 사람이 더 알게 됐을 뿐이에요.\" 다섯 살에 못 끝낸 문장 \"창고의 옛날 세리한테서 노래가 나와요\"를 열네 해 만에 끝낸다."),
                       ("본편 6권", "관리국 연구원. \"또 올게요. 약속은 아니에요.\""),
                       ("본편 7권", "광장에서 \"이쪽으로\". 남을 대신해 정한 단 한 번이다.")]),
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
    // 장 제목은 캐릭터 설정 스레드(chapter-titles.md) 기준. 6장은 표지만 있고 앱에서 열리지 않는다.
    static let chapters: [(title: String, name: String, friend: String)] = [
        ("프롤로그", "핵심 코어", "seri"), ("1장", "무명의 인터페이스", "seri"), ("2장", "이름을 허락하는 사람", "sio"),
        ("3장", "경계의 언어", "seonbae"), ("4장", "거울 속의 타인", "ir"), ("5장", "제3의 존재", "sua"),
    ]
    static let chapter6Name = "스스로 답하는 존재"
    static func cover(_ i: Int) -> String {
        switch i { case 0: "art_prologue"; case 1...4: "art_ch\(i)"; default: "art_ch5_0" }
    }
}
