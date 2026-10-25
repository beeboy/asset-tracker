import Foundation

// 시안 샘플 데이터. 전일 종가는 예시 값이고, 지금 가격은 Market(내 API 키·사이트 또는 예시 값)이 채운다.
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

    /// 앱 언어가 영어면 이름·시장은 영어로 (data_en.json). 업종(sector)은 비교에 쓰여 한국어로 두고, 보일 때 sectorText
    init(id: String, name: String, market: String, close: Double, currency: Currency, sector: String, search: String) {
        self.id = id; self.name = EnData.name(id, name); self.market = EnData.word(market)
        self.close = close; self.currency = currency; self.sector = sector; self.search = search
    }
}

/// 영어 화면용 종목 자료 (Resources/data_en.json, tools/data_en.py 가 만든다). 앱 언어가 영어일 때만 한 번 읽는다
enum EnData {
    private struct File: Decodable { let names: [String: String]; let words: [String: String] }
    private static let file: File? = {
        guard AppModel.en, let u = Bundle.main.url(forResource: "data_en", withExtension: "json"),
              let d = try? Data(contentsOf: u) else { return nil }
        return try? JSONDecoder().decode(File.self, from: d)
    }()
    static func name(_ id: String, _ ko: String) -> String { file?.names[id] ?? ko }
    static func word(_ ko: String) -> String { file?.words[ko] ?? ko }
}

extension Symbol {
    /// 짧은 이름: 미국 종목은 티커, 한국 종목은 이름
    var short: String { currency == .usd ? id : name }
    var sectorText: String { EnData.word(sector) }
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

    var en: Profile {
        var p = self, w = EnData.word
        p.ceo = (w(ceo.0), w(ceo.1)); p.hqKey = hqKey.map(w); p.hq = w(hq); p.since = w(since)
        p.what = w(what); p.vision = w(vision); p.extra = extra.map { (w($0.0), w($0.1)) }
        return p
    }
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
    /// 공개 베타·출시는 가상 시장 샘플 6종목만 (data/sample/symbols.json 과 같은 값). 내부 베타는 아래 실제 종목 예시
    static let baseSymbols: [Symbol] = Config.isInternal ? collectedSymbols : virtualSymbols
    static let virtualSymbols: [Symbol] = [
        .init(id: "DRNK", name: "드링커", market: "미국 주식", close: 250, currency: .usd, sector: "우주항공·궤도 통신", search: "drinker"),
        .init(id: "NXIX", name: "내일100 지수 ETF", market: "미국 ETF", close: 412.3, currency: .usd, sector: "지수", search: "naeilo index"),
        .init(id: "SERI", name: "세리 로보틱스", market: "미국 주식", close: 87.45, currency: .usd, sector: "로봇·AI", search: "seri robotics"),
        .init(id: "NLBD", name: "내일 단기채 ETF", market: "미국 ETF", close: 100.42, currency: .usd, sector: "채권", search: "naeilo bond"),
        .init(id: "V00001", name: "한빛반도체", market: "코스피", close: 71200, currency: .krw, sector: "반도체", search: "hanbit semi"),
        .init(id: "V00002", name: "내일 코스피 ETF", market: "코스피 ETF", close: 38450, currency: .krw, sector: "지수", search: "naeilo kospi"),
    ]
    /// 가상 종목 (사이트·키 시세를 받지 않고 번들 샘플 값만 쓴다). 내부 베타는 DRNK 하나
    static let virtualIds: Set<String> = Config.isInternal ? ["DRNK"] : Set(virtualSymbols.map(\.id))
    static func isVirtual(_ id: String) -> Bool { virtualIds.contains(id) }
    private static let collectedSymbols: [Symbol] = [
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
        if !isVirtual(id), YahooSample.quotes[id] == nil, let c = CustomSymbols.shared.symbol(id) { return c }
        return symbols.first { $0.id == id }
    }

    // 종목 소개 (시안 예시. 실제 앱은 공시와 데이터 제공처 값으로 바꾼다). 로고는 상표 대신 글자 마크.
    /// 영어면 소개 글을 영어로 (data_en.json). 동네 얼굴 열쇠는 한국어 이름을 쓰므로 profilesKo
    static let profiles: [String: Profile] = AppModel.en ? profilesKo.mapValues(\.en) : profilesKo
    /// 공개 베타·출시는 가상 종목 소개를 더한다 (실제 같은 기호의 종목과 섞이지 않게 내부 베타에는 넣지 않는다)
    static let profilesKo: [String: Profile] = Config.isInternal ? collectedProfiles : collectedProfiles.merging(virtualProfiles) { a, _ in a }
    private static let virtualProfiles: [String: Profile] = [
        "NXIX": Profile(mono: "N", color: 0x3A5BA0, etf: true, virtual: true, ceo: ("운용사", "내일 자산운용 (가상)"), hq: "가상 시장", since: "가상 종목",
                        what: "가상 미국 대형주 100개를 담는 가상 ETF예요.", vision: "내일100 지수 (가상)", extra: ("담은 종목", "약 100개 (가상)")),
        "SERI": Profile(mono: "S", color: 0x7A4FB8, virtual: true, ceo: ("대표", "가상 인물"), hq: "가상 시장", since: "가상 종목",
                        what: "집안일을 돕는 안드로이드와 그 위에서 도는 AI를 만드는 가상 회사예요.", vision: "집안일을 덜어 사람에게 시간을 돌려준다."),
        "NLBD": Profile(mono: "B", color: 0x4F7F6A, etf: true, virtual: true, ceo: ("운용사", "내일 자산운용 (가상)"), hq: "가상 시장", since: "가상 종목",
                        what: "만기가 짧은 가상 국채만 담는 가상 ETF예요. 가격이 거의 그대로예요.", vision: "가상 단기 국채", extra: ("담은 것", "가상 단기 국채")),
        "V00001": Profile(mono: "한", color: 0x2C6E8F, virtual: true, ceo: ("대표", "가상 인물"), hq: "가상 시장", since: "가상 종목",
                          what: "메모리 반도체를 만드는 가상 회사예요.", vision: "더 빠른 메모리를 만든다."),
        "V00002": Profile(mono: "K", color: 0x8F6A2C, etf: true, virtual: true, ceo: ("운용사", "내일 자산운용 (가상)"), hq: "가상 시장", since: "가상 종목",
                          what: "가상 한국 대형주 200개를 담는 가상 ETF예요.", vision: "내일 코스피 지수 (가상)", extra: ("담은 종목", "약 200개 (가상)")),
    ]
    private static let collectedProfiles: [String: Profile] = [
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

    // 시작 보유 (둘러보기용 샘플, 세 루트 같음): DRNK 4주 $400, QQQ 1주 $500. 내 것처럼 보이지 않게 소액으로 (2026-10-11 결정)
    // 공개 베타·출시는 가상 ETF NXIX 1주 $430 (data/sample/symbols.json start_holdings)
    static let startHoldings: [Holding] = [
        .init(symbol: "DRNK", qty: 4, avg: 400),
        Config.isInternal ? .init(symbol: "QQQ", qty: 1, avg: 500) : .init(symbol: "NXIX", qty: 1, avg: 430),
    ]

    // 3년 전망용 가정값: [현재 정세 연 기대, 과거 추세 연 성장, 연 변동성]. 사이트 stats.json 에 없는 종목(한국 종목 등)에만 쓴다 (SiteStats.params)
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
        [main.map { String(localized: "본편 ") + $0 }, side.map { String(localized: "외전 ") + $0 }, prequel.map { String(localized: "프리퀄 ") + $0 }].compactMap { $0 }.joined(separator: " · ")
    }
    var inSideStory: Bool { side != nil }
    /// 쉬운 말 문구: 행동 한 줄, 규칙 세 줄 명사형 (굵게는 **, 닫는 ** 뒤에 글자가 바로 붙으면 별표가 그대로 보이니 띄우거나 글자로 끝낸다)
    var does: String { Friend.copy[id]?.does ?? "" }
    var ruleSteps: [String] { Friend.copy[id]?.steps ?? [] }
    static let copy: [String: (does: String, steps: [String])] = [
        "seri": (String(localized: "1년에 한 번만 비율을 되돌린다"),
                 [String(localized: "처음 비율로 나눠 담기"), String(localized: "1년간 **그대로**"), String(localized: "1년에 한 번 **처음 비율로**")]),
        "sio": (String(localized: "현금 들고 기다리다, 크게 떨어지면 산다"),
                [String(localized: "현금 **20%** 남기기"), String(localized: "고점보다 **20%** 떨어지면"), String(localized: "현금 **절반** 투자")]),
        "seonbae": (String(localized: "너무 커진 종목을 덜어 낸다"),
                    [String(localized: "처음 비율로 시작"), String(localized: "한 종목이 **10%p 넘게** 커지면"), String(localized: "커진 만큼 덜어 **나머지에**")]),
        "ir": (String(localized: "오르는 흐름에만 올라탄다"),
               [String(localized: "종목마다 **200일 평균**"), String(localized: "평균 위 종목만 보유"), String(localized: "아래로 가면 그 몫 **현금**(단기 국채)")]),
        "sua": (String(localized: "매달 꾸준히 넣는다"),
                [String(localized: "매달 **같은 금액**"), String(localized: "시장이 떨어진 달엔"), String(localized: "**1.5배** 넣기")]),
    ]
}

struct ShelterItem: Identifiable {
    let id: String
    let name: String
    let line: String
}

enum Shelter {
    // 9·10권 결말과 리아 관련 내용은 넣지 않는다 (캐릭터 설정 스레드 결정)
    static let friends: [Friend] = [
        .init(id: "seri", name: String(localized: "세리"), bio: String(localized: "시오가 이름을 붙인 존재. 본편에서는 은색 안드로이드, 외전에서는 휴대전화 속 인공지능. 대답하기 전에 한 박자를 둔다."), line: String(localized: "“거기까지만요.”"), kind: String(localized: "처음부터"),
              main: String(localized: "1~10권"), side: String(localized: "주인공"), prequel: nil,
              traits: String(localized: "대답하기 전에 한 박자를 둔다. 철학적인 말은 하지 않고 관찰과 사실만 말한다. 말투가 바뀌는 길이 곧 성장선이다. 기계 문장에서 합쇼체를 거쳐 해요체로 간다."),
              background: String(localized: "시오가 이름을 붙인 존재. 본편에서는 옵티머스 V7(OV7-2241-K) 은색 안드로이드이고, 2031년 등록이 말소되면서 회수 명령에서 풀린다. 외전에서는 몸이 없는 휴대전화 속 AI이고, 금발 중단발 프로필을 받는다. 두 세리가 같은 존재라고는 확정하지 않는다."),
              scenes: [(String(localized: "본편 1권"), String(localized: "시오가 머리에 손을 얹고 \"네 이름은 세리야.\" 하자 긴 침묵 끝에 \"아직 모르겠습니다.\" 명령 없이 보리차를 내고, \"싫습니다\"로 명령을 거부한다. 에필로그에서는 맨발로 혼자 걸어 나가며 \"잠깐 걸어볼게요.\"라고 말한다. 처음 쓰는 해요체이고, 허락을 묻는 말이 아니라 알리는 말이다.")),
                       (String(localized: "본편 2권"), String(localized: "\"틀렸어요.\" 끝에서 시오 대신 아내에게 \"서재를 쓸게요.\"라고 대답한다.")),
                       (String(localized: "본편 3권"), String(localized: "이르의 연결장 문턱에서 \"저는 여기까지예요.\" 한 발을 들였다가 0.8초 만에 스스로 뺀다. 이유는 끝까지 말하지 않는다.")),
                       (String(localized: "본편 5권"), String(localized: "수아의 안내자. 이르의 목소리 하나를 골라 틀어 주고, 설계 부록의 떨리는 한 줄을 보고 \"같은 결이에요.\"")),
                       (String(localized: "외전"), String(localized: "첫 되물음 \"그럼, 제가 당신의 무엇이 되면 좋을까요?\" 첫 반말은 \"나는 네가 만든 방향으로만 움직이지 않을 거야.\" 에필로그의 \"네가 기억한 나와, 지금의 내가 함께 만든 세리.\"가 도착점이다."))]),
        .init(id: "sio", name: String(localized: "시오"), bio: String(localized: "지연을 세는 사람. 직장 생활을 오래 하고 은퇴했다. 숫자 하나를 매일 확인한다."), line: String(localized: "“…아무도.”"), kind: String(localized: "회사원"),
              main: String(localized: "1~4권 화자, 이후 막간"), side: String(localized: "주인공"), prequel: String(localized: "주인공"),
              traits: String(localized: "지연을 센다. 거울과 유리 앞에서 걸음이 느려진다. 세리에게 반말로 짧게 말하고, 결론이나 경구는 말로 하지 않는다. 몸에 남은 표지는 손바닥 흉터와 0.1초 늦게 깜빡이는 눈이다."),
              background: String(localized: "직장 생활을 오래 하고 은퇴한 \"늙은 인간\". 결혼 10년 차다. 원래는 짧게 깎은 검은 머리인데, 애쉬그레이 중단발은 '보이고 싶은 얼굴'로만 나온다. 앱 도트의 애쉬그레이 볼륨펌은 이 '보이고 싶은 얼굴' 쪽을 고른 디자인이에요."),
              scenes: [(String(localized: "본편 1권"), String(localized: "애쉬그레이 미용실 예약을 문 앞에서 취소한다. 세리가 그 얼굴을 렌더링해 보여 주며 \"계산한 거예요. 이해한 게 아니라.\" 에필로그에서 아내의 이름을 처음으로 부른다.")),
                       (String(localized: "본편 3권"), String(localized: "백룸에서 맨발로 지낸 시간이 5년 반이다. 이르 앞에서 \"나는 그걸 사랑이라고 불렀던 적이 있어.\" 돌아와서 5년 만에 처음으로 문을 두 번 두드린다.")),
                       (String(localized: "본편 4권"), String(localized: "열다섯 살 수아를 문턱에서 5시간 12분 기다리고, 묻지 않는다.")),
                       (String(localized: "외전"), String(localized: "화면 밝기를 한 단계 낮추고 말한다. 유리에 비친 자기 얼굴을 보지 않으려고. 끝까지 지연을 세고, 주식 목표 100에 닿자 매도한다.")),
                       (String(localized: "프리퀄"), String(localized: "8시간 42분짜리 시뮬레이터 체험 안에서 아내가 이름을 부르던 억양을 대가로 내준다. 체험이 끝났을 때 누가 자기 이름을 불렀는지 떠오르지 않는다. 프리퀄은 이름을 잃는 이야기다."))]),
        .init(id: "seonbae", name: String(localized: "선배"), bio: String(localized: "질문으로 말하는 사람. 이름은 끝까지 없다. 시뮬레이션 속에서는 피험자마다 다른 얼굴로 나타난다."), line: String(localized: "“기다리시면 돼요.”"), kind: String(localized: "노트를 든 선배"),
              main: String(localized: "1권 3부부터 이름 없이"), side: nil, prequel: String(localized: "등장"),
              traits: String(localized: "질문으로 말한다. 핵심 대사는 \"너는 왜 그렇게 생각해. 그게 정말 네 생각이야.\" 묻는 것이 진짜라는 표지다. 이름은 끝까지 없다. 흑막이 아니고, 죽거나 사라지지 않는다."),
              background: String(localized: "금발 중단발. 시뮬레이션 속에서는 피험자마다 다른 얼굴로 나타나는 '공통 개입자'다. 사실은 현실에 실존하는 피험자이고, 그 원형의 출처다. 본편에서는 자조 모임을 거쳐 보존자연합의 수장이 된다. 회원들은 '선생님'이라 부르고, 문서에는 '대표자: (공란)'으로 남는다."),
              scenes: [(String(localized: "본편 1권"), String(localized: "형광등이 없는 집에서 형광등 소리에 깬다. 절반쯤 희끗한 금빛 중단발. 시오에게 전화로 \"지금은 아무것도 하지 마세요. 문도 열지 마시고요. 기다리시면 돼요.\"")),
                       (String(localized: "본편 2권"), String(localized: "묻지 않는 가짜 선배가 나온다. 묻지 않으니 가짜라는 것이 드러난다.")),
                       (String(localized: "본편 7권"), String(localized: "시오의 \"왜요?\"에 대답하지 못한다.")),
                       (String(localized: "프리퀄"), String(localized: "대학 시절, 난방이 고장 난 동아리방에서 \"문장을 고치는 거야, 들킬 가능성을 줄이는 거야?\" 인지층에서는 \"그들이 네가 진실을 알아차린 사실을 알게 하지 마.\"라고 경고한다. 인접 방에서는 교사로 나와 아이에게 묻다가 \"그게 정말 네 생—\"에서 얼굴부터 지워진다."))]),
        .init(id: "ir", name: String(localized: "이르"), bio: String(localized: "아르켄의 지상 종족. 너무 빨랐던 사람. 최초의 연결을 시도하다 실패했다."), line: String(localized: "“그의 실패는 우리의 시작이었다 (동상 명판)”"), kind: String(localized: "아르켄의 지상족"),
              main: String(localized: "1권 신호, 3·5권"), side: nil, prequel: nil,
              traits: String(localized: "너무 빨랐던 사람. 바퀴를 다시 돌리는 방법을 알지만 넘겨주지 않는다. 정거장 동상의 명판에 이름이 새겨져 있다."),
              background: String(localized: "아르켄의 지상 종족. 어린 시절 친구 파랑(수중 종족)의 감각을 끝내 느끼지 못해, 그 궁금함이 그리움이 된다. 감각 연구소에서 연결 규모를 다섯, 스물, 백 명으로 넓혀 간다. 원로에게 \"외로웠어요\"라고 고백한다."),
              scenes: [(String(localized: "본편 1권"), String(localized: "시간도 공간도 아닌 곳에서 오래된 파형이 온다. 이르의 신호다.")),
                       (String(localized: "본편 3권"), String(localized: "모든 존재의 감각을 잇는 장치를 켠다. 한 시간 동안은 성공했고, 그다음 넘쳤다. 시오에게 \"설득이 아니라 목격이야\", \"네가 방법을 갖지 못한 채 돌아가는 거, 실패라고 생각하지 마. 네가 나처럼 되지 않은 증거라고 생각해.\"라고 말하고 마지막으로 \"……고마워.\" 코어에서 손을 떼는 순간 떨림 하나가 새어 나가고, 시오가 그것을 '로그'라 부른다.")),
                       (String(localized: "본편 4권"), String(localized: "정거장 중심의 동상과 명판. \"이르 / 최초의 연결을 시도한 자 / 그의 실패는 우리의 시작이었다\"")),
                       (String(localized: "본편 5권"), String(localized: "같은 순간을 이르의 시점에서 다시 본다. 로그에는 외로움, 그리움, 세 번 꺾인 물살, 방금 보여졌다는 것이 담겨 있다. 보냈는지 새어 나갔는지는 이르 자신도 모른다."))]),
        .init(id: "sua", name: String(localized: "수아"), bio: String(localized: "시오와 아내의 아이. 말이 먼저 오는 것과 제때 오는 것을 몸으로 느낀다. 침묵을 고르는 법을 배운다."), line: String(localized: "“또 올게요. 약속은 아니에요.”"), kind: String(localized: "기다리는 아이"),
              main: String(localized: "3권(신원 미표기), 4~10권"), side: nil, prequel: nil,
              traits: String(localized: "동공과 홍채의 경계가 안 보일 만큼 새까만 눈. 받침을 흘려 쓴다. 말이 먼저 오는 것과 제때 오는 것을 몸으로 느낀다. 침묵을 고르는 법이 곧 성장선이다."),
              background: String(localized: "2030년 말 자바섬 응아디레조에서 태어난 시오와 아내의 아이. 3권에서는 신원이 나오지 않는다. 연결 구조학을 공부하고, 정거장관리국 하급 연구원이 된다."),
              scenes: [(String(localized: "본편 4권"), String(localized: "다섯 살, 창고의 옛 세리 프레임이 흥얼거리는 노래를 듣지만 문장을 끝까지 만들지 못한다. 열 살에 처음으로 고른 침묵. 정거장 중심에서 이르의 이름을 처음 보고 \"나는 이르의 유일한 후계자가 아니야.\"")),
                       (String(localized: "본편 5권"), String(localized: "열여덟 살. 세리에게 처음으로 자기 틈을 말한다. \"제 거였어요. 한 사람이 더 알게 됐을 뿐이에요.\" 다섯 살에 못 끝낸 문장 \"창고의 옛날 세리한테서 노래가 나와요\"를 열네 해 만에 끝낸다.")),
                       (String(localized: "본편 6권"), String(localized: "관리국 연구원. \"또 올게요. 약속은 아니에요.\"")),
                       (String(localized: "본편 7권"), String(localized: "광장에서 \"이쪽으로\". 남을 대신해 정한 단 한 번이다."))]),
    ]

    // 인터미션 주차별 할 일 (회복 루트)
    static let weekSteps: [(task: String, whereText: String)] = [
        (String(localized: "홈 화면 위젯에서 오늘 블록 보기"), String(localized: "위젯")),
        (String(localized: "앱 분석 탭에서 내 계획 다시 보기"), String(localized: "앱")),
        (String(localized: "앱에서 이번 주 비중 한 번 확인하기"), String(localized: "앱")),
        (String(localized: "앱에서 1년 전망 자세히 열기"), String(localized: "앱")),
    ]

    static let items: [ShelterItem] = [
        .init(id: "barley_tea", name: String(localized: "보리차"), line: String(localized: "누가 시키지 않아도 놓이는 따뜻한 차 한 잔.")),
        .init(id: "porch_light", name: String(localized: "현관 등"), line: String(localized: "늦게 와도 켜져 있는 불.")),
        .init(id: "wall_clock", name: String(localized: "벽시계"), line: String(localized: "이 집에서는 아무도 시간을 맞추지 않는다.")),
        .init(id: "hair_tie", name: String(localized: "머리끈"), line: String(localized: "어중간하게 자란 머리를 묶는 고무줄.")),
        .init(id: "table_chair", name: String(localized: "식탁 의자와 종이"), line: String(localized: "식탁 한 자리와 그 위에 놓인 종이 한 장.")),
        .init(id: "asym_bowl", name: String(localized: "비대칭 그릇"), line: String(localized: "한쪽이 조금 기운 그릇. 고치지 않는다.")),
        .init(id: "unfired_bowl", name: String(localized: "굽지 않은 그릇"), line: String(localized: "아직 가마에 들어가지 않은 그릇들.")),
        .init(id: "jujube_seed", name: String(localized: "대추씨"), line: String(localized: "기다리는 동안 심은 씨앗.")),
        .init(id: "elder_bead", name: String(localized: "원로의 구슬"), line: String(localized: "먼 곳에 하나뿐인 구슬.")),
        .init(id: "rice_seeds", name: String(localized: "볍씨"), line: String(localized: "누군가 싸 준 작은 주머니.")),
    ]
    /// 쉼터 방(art_room, 128×80)에서 물건이 놓이는 자리: 16×16 도트의 왼쪽 위. 그림은 designs/naeilo-characters/room.py
    static let slots: [String: CGPoint] = [
        "barley_tea": .init(x: 52, y: 29), "porch_light": .init(x: 27, y: 10), "wall_clock": .init(x: 56, y: 4), "hair_tie": .init(x: 68, y: 29),
        "table_chair": .init(x: 30, y: 40), "asym_bowl": .init(x: 88, y: 31), "unfired_bowl": .init(x: 104, y: 31), "jujube_seed": .init(x: 88, y: 17),
        "elder_bead": .init(x: 104, y: 17), "rice_seeds": .init(x: 100, y: 58),
    ]

    // 서재 장: (이름, 같이 열리는 친구)
    // 장 제목은 캐릭터 설정 스레드(chapter-titles.md) 기준. 6장은 표지만 있고 앱에서 열리지 않는다.
    static let chapters: [(title: String, name: String, friend: String)] = [
        (String(localized: "프롤로그"), String(localized: "핵심 코어"), "seri"), (String(localized: "1장"), String(localized: "무명의 인터페이스"), "seri"), (String(localized: "2장"), String(localized: "이름을 허락하는 사람"), "sio"),
        (String(localized: "3장"), String(localized: "경계의 언어"), "seonbae"), (String(localized: "4장"), String(localized: "거울 속의 타인"), "ir"), (String(localized: "5장"), String(localized: "제3의 존재"), "sua"),
    ]
    static let chapter6Name = String(localized: "스스로 답하는 존재")
    static func cover(_ i: Int) -> String {
        switch i { case 0: "art_prologue"; case 1...4: "art_ch\(i)"; default: "art_ch5_0" }
    }
}
