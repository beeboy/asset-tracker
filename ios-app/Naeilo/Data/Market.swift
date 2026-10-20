import Foundation
import Observation

// 지금 시세. 사용자가 넣은 내 오픈 API 키(토스·Alpaca·Longbridge)로 받고, 없으면 naeilo 사이트 값.

struct Quote {
    let last: Double        // 지금 가격 (실시간이 아니면 전일 종가)
    let prevClose: Double   // 전일 종가
    let live: Bool
    var change: Double { prevClose > 0 ? last / prevClose - 1 : 0 }
}

@Observable
final class Market {
    static let shared = Market()

    var updatedAt = Date()
    /// 사이트가 30분마다 모으는 시세 (앱·위젯이 같이 받는다). 없으면 빌드 때 넣은 테스트 자료
    var site: QuoteFeed.File? = QuoteFeed.cached()
    /// 토스증권 지금 가격 (개발자 전용, 종목 id → 값). 있으면 Yahoo 값보다 먼저
    var toss: QuoteFeed.File? = TossFeed.cached()
    /// 내 Alpaca 키로 받은 미국 종목 지금 가격 (종목 id → 값). 토스 값이 없을 때 사이트 값보다 먼저
    var alpaca: QuoteFeed.File? = AlpacaFeed.cached()
    /// Alpaca 받기를 마친 때. 설정 > 내 Alpaca 키 화면이 이걸 보고 다시 그린다
    var alpacaDone = Date.distantPast
    /// 내 Longbridge 키로 받은 지금 가격 (종목 id → 값). 토스·Alpaca 값이 없을 때 사이트 값보다 먼저
    var longbridge: QuoteFeed.File? = LongbridgeFeed.cached()
    /// Longbridge 받기를 마친 때. 설정 > 내 Longbridge 키 화면이 이걸 보고 다시 그린다
    var longbridgeDone = Date.distantPast
    /// 토스 시세를 받는 중인 일 (겹쳐 묻지 않게). 다시 받기는 이 일이 끝나길 기다렸다가 한 번 더 받는다
    @ObservationIgnored private var tossTask: Task<Void, Never>? = nil
    /// 토스 받기를 마친 때 (성공·실패 모두). 설정 > 내 토스 키 화면이 이걸 보고 다시 그린다
    var tossDone = Date.distantPast
    /// 매매기준율 (사이트 data/mar.json, 서울외국환중개). 웹 평가액과 같은 환율
    var mar: MarFeed.File? = MarFeed.cached()
    /// 원화 평가에 쓰는 원/달러: 웹 fxBase 와 같게 매매기준율, 없거나 5일 넘게 묵었으면 지금 환율
    /// 평가 환율: 매매기준율 먼저 (토스 총자산이 이걸로 센다). 없으면 토스 원/달러, 그다음 지금 환율
    var valFx: Double { marRate ?? tossFx?.rate ?? fx.last }
    var marRate: Double? { marUse?.rate }
    /// 지금 쓰는 매매기준율 (값, 고시 날짜). 토스처럼 새 날짜 고시는 그날 오전 8시 59분(한국)부터, 그 전엔 앞 고시. 5일 넘게 묵었으면 nil
    var marUse: (rate: Double, date: String)? {
        guard let m = mar, m.rate > 0 else { return nil }
        let u = MarFeed.started(m.date) ? (m.rate, m.date) : (m.prev.map { ($0.rate, $0.date) } ?? (m.rate, m.date))
        return u.0 > 0 && Day.dday(u.1) >= -5 ? (u.0, u.1) : nil
    }
    /// 종목별 전망 입력값 (사이트가 매일 만든다). 없으면 빌드 때 넣은 파일
    var stats: SiteStats.File? = SiteStats.load()
    /// 내 토스 키로 받은 지난 종가(일봉)·원/달러. 키를 넣은 기기만
    var tossHist: TossHistory.File? = TossHistory.cached()
    /// 토스 원/달러 (하루 안에 받은 값만)
    var tossFx: TossHistory.FX? { tossHist?.fx.flatMap { Date().timeIntervalSince1970 - $0.got < 86400 ? $0 : nil } }
    /// 사이트 지난 종가 (웹 자산 추이·목표 진행과 같은 data/prices 파일). 내 종목과 원/달러만.
    /// 내 토스 키로 받은 종목은 토스 일봉으로 바꿔 둔다 (TossHistory.merged)
    var prices: SitePrices.File? = TossHistory.merged(SitePrices.cached(), TossHistory.cached())
    var source: String { site != nil ? String(localized: "naeilo 사이트") : YahooSample.quotes.isEmpty ? String(localized: "예시 값") : String(localized: "Yahoo 중계 (테스트)") }

    // 샘플 종목 DRNK 의 하루 움직임 (샘플 기록의 마지막 날). 다른 종목은 받은 시세가 없으면 움직임을 만들지 않는다
    static let todayMove: [String: Double] = ["DRNK": -0.0221]

    func quote(_ s: Symbol) -> Quote {
        let b = yahooQuote(s)
        if let t = tossLast(s) { return Quote(last: t, prevClose: tossPrev(s, b) ?? t, live: true) }
        // Alpaca·Longbridge: 전일 종가는 사이트(공식 종가)가 먼저, 없으면 받은 전일 종가
        guard let a = alpacaQ(s) ?? lbQ(s) else { return b }
        return Quote(last: a.last, prevClose: b.prevClose > 0 ? b.prevClose : a.prev > 0 ? a.prev : a.last, live: true)
    }
    /// Alpaca 값을 쓰는 종목 (종목 화면에 "Alpaca(IEX) 시세" 표시)
    func isAlpaca(_ s: Symbol) -> Bool { tossLast(s) == nil && alpacaQ(s) != nil }
    /// Longbridge 값을 쓰는 종목 (종목 화면에 "Longbridge 시세" 표시)
    func isLongbridge(_ s: Symbol) -> Bool { tossLast(s) == nil && alpacaQ(s) == nil && lbQ(s) != nil }
    private func lbQ(_ s: Symbol) -> QuoteFeed.Q? {
        guard s.id != "DRNK", s.currency == .usd, regClose(s) == nil, let q = longbridge?.q[s.id], q.last > 0 else { return nil }
        return q
    }
    private func alpacaQ(_ s: Symbol) -> QuoteFeed.Q? {
        guard s.id != "DRNK", s.currency == .usd, regClose(s) == nil, let q = alpaca?.q[s.id], q.last > 0 else { return nil }
        return q
    }
    /// 토스 값의 전일 종가. 사이트 시세가 토스 값보다 앞선 날 것이면 (주말·장중에 사이트가 아직 안 바뀜) 사이트 마지막 값이 전일 종가
    private func tossPrev(_ s: Symbol, _ b: Quote) -> Double? {
        // 토스 일봉이 있으면 그 값 (토스 값 거래일 앞 마지막 종가)
        if let t = toss?.q[s.id], let p = TossDaily.prev(tossDaily(s), at: t.at, kr: s.currency == .krw) { return p }
        if let t = toss?.q[s.id], let q = site?.q[YahooSample.keyFor[s.id] ?? CustomSymbols.yahoo(for: s)], q.at > 0, t.at > q.at {
            var cal = Calendar(identifier: .gregorian)
            cal.timeZone = TimeZone(identifier: s.currency == .krw ? "Asia/Seoul" : "America/New_York")!
            if !cal.isDate(Date(timeIntervalSince1970: q.at), inSameDayAs: Date(timeIntervalSince1970: t.at)) { return q.reg ?? q.last }
        }
        return b.prevClose > 0 ? b.prevClose : nil
    }
    /// 토스 값을 쓰는 종목 (종목 화면에 "토스 시세" 표시)
    func isToss(_ s: Symbol) -> Bool { tossLast(s) != nil }
    private func tossLast(_ s: Symbol) -> Double? {
        guard s.id != "DRNK", let t = toss?.q[s.id], t.last > 0 else { return nil }
        // 장이 끝난 때도 애프터마켓 뒤에 체결된 값(토스 주간거래)이면 그 값. 토스 총자산이 주간거래 가격으로 센다
        if regClose(s) != nil, !TossDaily.daytime(t.at) { return nil }
        return t.last
    }
    /// 내 토스 키로 받은 이 종목 일봉
    private func tossDaily(_ s: Symbol) -> TossDaily.Hist? {
        tossHist?.h[SiteStats.key(s.id)].map { .init(dates: $0.dates, closes: $0.closes) }
    }
    /// 토스가 지금 가격과 일봉을 다 주는 종목 (Yahoo 를 부르지 않는다)
    func tossCovers(_ id: String) -> Bool { toss?.q[id] != nil && tossHist?.h[SiteStats.key(id)] != nil }
    /// 미국 장이 다 끝났으면 사이트 정규장 종가. 토스·Alpaca 의 애프터마켓 가격 대신 이걸로 센다 (토스 총자산과 같은 기준)
    private func regClose(_ s: Symbol) -> Double? {
        guard s.currency == .usd, USSession.closed() else { return nil }
        if let t = toss?.q[s.id], let r = TossDaily.reg(tossDaily(s), at: t.at) { return r }
        if tossCovers(s.id) { return nil }   // 이 종목은 Yahoo 를 안 불러서 사이트 값이 묵었다
        guard let r = site?.q[YahooSample.keyFor[s.id] ?? CustomSymbols.yahoo(for: s)]?.reg, r > 0 else { return nil }
        return r
    }

    private func yahooQuote(_ s: Symbol) -> Quote {
        if s.id != "DRNK" {
            // 사이트 시세와 추가한 종목(5분마다 Yahoo 중계) 중 나중에 받은 값
            let c = CustomSymbols.shared.items.first { $0.id == s.id }
            if let f = site, let q = f.q[YahooSample.keyFor[s.id] ?? CustomSymbols.yahoo(for: s)],
               c == nil || c!.at.timeIntervalSince1970 < f.got {
                return Quote(last: regClose(s) ?? q.last, prevClose: q.prev, live: s.currency == .usd)
            }
            if let c { return Quote(last: c.last, prevClose: c.prev, live: c.usd) }
            // 한국 종목은 넣은 가격이 없으면 값이 없다 (시안 값을 쓰지 않는다)
            if s.currency == .krw { return Quote(last: 0, prevClose: 0, live: false) }
        }
        if let q = YahooSample.quotes[s.id] { return Quote(last: q.last, prevClose: q.prevClose, live: true) }
        if let m = Self.todayMove[s.id] { return Quote(last: s.close * (1 + m), prevClose: s.close, live: true) }
        return Quote(last: s.close, prevClose: s.close, live: false)
    }

    /// 원/달러 (지금, 전일)
    var fx: Quote {
        if let t = tossFx { return Quote(last: t.rate, prevClose: t.prev, live: true) }
        if let q = site?.q["KRW=X"] { return Quote(last: q.last, prevClose: q.prev, live: true) }
        if let q = YahooSample.quotes["FX"] { return Quote(last: q.last, prevClose: q.prevClose, live: true) }
        return Quote(last: Sample.fx, prevClose: Sample.fx, live: false)
    }

    var asOfText: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR")
        if let s = site, let t = s.q.values.map(\.at).max(), t > 0 {
            let d = Date(timeIntervalSince1970: t)
            f.dateFormat = Calendar.current.isDateInToday(d) ? "a h:mm" : String(localized: "M월 d일 a h:mm")
            let ago = Int(Date().timeIntervalSince1970 - s.got) / 60
            return String(localized: "\(String(describing: f.string(from: d))) 시세 · \(String(describing: ago < 1 ? String(localized: "방금") : String(localized: "\(String(describing: ago))분 전"))) 받음")
        }
        if let t = YahooSample.asOf {
            f.dateFormat = String(localized: "M월 d일 a h:mm")
            return String(localized: "\(String(describing: f.string(from: t))) 시세 (테스트 자료)")
        }
        f.dateFormat = "a h:mm"
        return String(localized: "지금 시세 · \(String(describing: f.string(from: updatedAt))) 갱신")
    }

    /// force: 아래로 당겨 갱신할 때. 받은 지 얼마 안 됐어도 다시 받는다
    func refresh(held: Set<String> = [], force: Bool = false) async {
        // 토스 시세 (개발자 주소를 넣었거나, 사용자가 설정 > 토스증권 시세에 내 키를 넣은 기기만): 국내·미국 종목.
        // 평소에는 기다리지 않고 따로 받아 도착하면 반영한다 (앱 시작을 늦추지 않게). 당겨 갱신·다시 받기(force)는 토스까지 기다린다.
        // 1분 안에 받았으면 그대로. 실패하면 15분 안에 받은 값, 그것도 없으면 nil → Yahoo 값
        async let tossWait: Void = force ? refreshToss(held: held, force: true) : ()
        if !force { Task { await refreshToss(held: held, force: false) } }
        // Alpaca 시세 (설정 > 내 Alpaca 키를 넣은 기기만): 미국 종목. 토스와 같은 방식
        async let alpacaWait: Void = force ? refreshAlpaca(held: held, force: true) : ()
        if !force { Task { await refreshAlpaca(held: held, force: false) } }
        // Longbridge 시세 (설정 > 내 Longbridge 키를 넣은 기기만): 지금은 미국 종목. 같은 방식
        async let lbWait: Void = force ? refreshLongbridge(held: held, force: true) : ()
        if !force { Task { await refreshLongbridge(held: held, force: false) } }
        // 나머지 시세는 한꺼번에 받는다 (차례로 기다리지 않게)
        // 지난 종가: 30분마다 사이트 index.json 만 보고, 새로 모았을 때만 내 종목 파일을 다시 받는다 (내 길·전망 기준값이 웹과 같은 종가)
        let keys = held.isEmpty ? Set(prices?.h.keys.map { $0 } ?? []) : Set(held.filter { $0 != "DRNK" }.map(SiteStats.key)).union(KoreaEstimate.baseKeys(held))
        let oldPrices = SitePrices.cached()   // 토스 값을 섞지 않은 사이트 값
        // 지금 가격: 미국 종목·국내 개별 종목·원/달러를 Yahoo 에서 직접 (못 받은 것만 사이트). 4분 안에 받았으면 그대로
        if !held.isEmpty {
            let syms = held.compactMap { id -> String? in
                guard id != "DRNK", id != KoreaEstimate.sumId, let s = Sample.symbol(id) else { return nil }
                guard s.currency == .usd || KoreaEstimate.popular.contains(id), !tossCovers(id) else { return nil }
                return YahooSample.keyFor[id] ?? CustomSymbols.yahoo(for: s)
            }
            Store.quoteSyms = (Set(syms).union(tossFx == nil ? ["KRW=X"] : [])).sorted()
        }
        async let siteNew = QuoteFeed.fetch(minAge: force ? 0 : 240)
        async let marNew = MarFeed.fetch(minAge: force ? 0 : 1800)
        // 토스 일봉이 있는 종목은 빼고 (원/달러·지수는 토스에 일봉이 없어 늘 Yahoo)
        let tossKeys = Set(TossKeys.on ? (tossHist?.h.keys).map(Array.init) ?? [] : [])
        async let pricesNew = SitePrices.fetch(keys: keys.subtracting(tossKeys).union(["KRW=X"]).union(Parallel.wantKeys), old: oldPrices, minAge: force ? 0 : 1800)
        // 전망 입력값: 하루 한 번 바뀌므로 6시간에 한 번 (당겨 갱신하면 바로)
        async let statsNew = SiteStats.fetch(minAge: force ? 0 : 6 * 3600)
        // 추가한 종목: 내 종목에 있는 것만 5분에 한 번 (당겨 갱신하면 바로)
        async let customDone: Void = CustomSymbols.shared.refresh(held: held, force: force)
        async let extraDone: Void = ExtraStats.shared.refresh(Parallel.wantKeys)
        if let f = await siteNew { site = f }
        if let r = await marNew { mar = r }
        if let p = await pricesNew { prices = TossHistory.merged(p, tossHist) }
        if let s = await statsNew { stats = s }
        await customDone
        await extraDone
        await tossWait
        await alpacaWait
        await lbWait
        updatedAt = Date()
    }

    /// 토스 지금 가격과 지난 종가·원/달러를 받는다. 받는 중이면 평소에는 건너뛰고, force 면 그 일이 끝난 뒤 새로 한 번 더 받는다
    @MainActor
    func refreshToss(held: Set<String>, force: Bool) async {
        var ty: [String: String] = [:]
        for id in held where id != "DRNK" && id != KoreaEstimate.sumId { if let s = Sample.symbol(id) { ty[id] = CustomSymbols.yahoo(for: s) } }
        guard !ty.isEmpty, TossFeed.base != nil || TossKeys.on else { return }
        if let t = tossTask {
            if !force { return }
            await t.value     // 받는 중인 일이 끝나길 기다렸다가 새로 받는다 (끝난 일을 거듭 기다리며 도는 일이 없게 한 번만)
        }
        let t = Task { @MainActor in
            let q = await TossFeed.fetch(ty, minAge: force ? 0 : 60, force: force)
            self.toss = q
            // 지난 종가·원/달러도 내 키로 (종목마다 하루 한 번, 환율은 10분에 한 번)
            if let h = await TossHistory.fetch(Dictionary(ty.map { (SiteStats.key($0.key), $0.value) }, uniquingKeysWith: { a, _ in a }), force: force) {
                self.tossHist = h
                self.prices = TossHistory.merged(SitePrices.cached(), h)
            }
            self.tossDone = Date()
        }
        tossTask = t
        await t.value
        if tossTask == t { tossTask = nil }
    }

    /// Alpaca 지금 가격을 받는다 (1분 안에 받았으면 그대로, 당겨 갱신은 바로)
    @MainActor
    func refreshAlpaca(held: Set<String>, force: Bool) async {
        guard AlpacaKeys.on else { if alpaca != nil { alpaca = nil }; return }
        var ty: [String: String] = [:]
        for id in held where id != "DRNK" { if let s = Sample.symbol(id), s.currency == .usd { ty[id] = CustomSymbols.yahoo(for: s) } }
        guard !ty.isEmpty else { return }
        alpaca = await AlpacaFeed.fetch(ty, minAge: force ? 0 : 60)
        alpacaDone = Date()
    }

    /// Longbridge 지금 가격을 받는다 (1분 안에 받았으면 그대로, 당겨 갱신은 바로)
    @MainActor
    func refreshLongbridge(held: Set<String>, force: Bool) async {
        guard LongbridgeKeys.on else { if longbridge != nil { longbridge = nil }; return }
        var ty: [String: String] = [:]
        for id in held where id != "DRNK" { if let s = Sample.symbol(id), s.currency == .usd { ty[id] = CustomSymbols.yahoo(for: s) } }
        guard !ty.isEmpty else { return }
        longbridge = await LongbridgeFeed.fetch(ty, minAge: force ? 0 : 60)
        longbridgeDone = Date()
    }

    /// 아래로 당겨 갱신: 내 종목을 바로 받고 위젯에도 넘긴다
    @MainActor
    func pullRefresh(_ m: AppModel) async {
        await refresh(held: Set(m.holdings.map(\.symbol)), force: true)
        if m.persists { m.recordDay() }     // 내 기록: 새로 받은 시세로 오늘 줄
        LineClock.shared.next()     // 당기면 인물 대사도 다음 줄로
        WidgetBridge.write(m)
    }
}

extension Symbol {
    var quote: Quote { Market.shared.quote(self) }
    /// 지금 가격
    var last: Double { quote.last }
}

// 테스트 시세: 사이트가 모으는 Yahoo 중계 자료 (data/prices/*.json 3년 종가, data/quotes.json 전일 종가·현재가).
// DRNK 는 가상 종목이라 시안 값, 한국 종목과 MSFT 는 자료가 없어 시안 값을 쓴다.
enum YahooSample {
    struct Hist: Codable { let dates: [String]; let closes: [Double] }
    struct Q { let prevClose: Double; let last: Double; let time: Date }

    static let keyFor: [String: String] = ["QQQ": "QQQ", "AAPL": "AAPL", "NVDA": "NVDA", "SPY": "SPY", "FX": "KRW=X"]
    private static func file(_ k: String) -> String { k.replacingOccurrences(of: "=", with: "_").replacingOccurrences(of: "^", with: "_") }

    static let quotes: [String: Q] = {
        guard let url = Bundle.main.url(forResource: "quotes", withExtension: "json"),
              let d = try? Data(contentsOf: url),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: [String: Any]] else { return [:] }
        var out: [String: Q] = [:]
        for (id, k) in keyFor {
            guard let v = o[k], let prev = v["prev_close"] as? Double, let last = (v["last"] ?? v["regular"]) as? Double else { continue }
            out[id] = Q(prevClose: prev, last: last, time: Date(timeIntervalSince1970: (v["last_time"] as? Double) ?? 0))
        }
        return out
    }()

    static let history: [String: Hist] = {
        var out: [String: Hist] = [:]
        for (id, k) in keyFor {
            guard let url = Bundle.main.url(forResource: file(k), withExtension: "json", subdirectory: "prices"),
                  let d = try? Data(contentsOf: url),
                  let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                  let dates = o["dates"] as? [String], let c = o["close"] as? [Any] else { continue }
            let closes = c.map { ($0 as? Double) ?? .nan }
            // 빈 값은 앞 값으로 메운다
            var filled: [Double] = []; var prev = closes.first { !$0.isNaN } ?? 1
            for v in closes { if !v.isNaN { prev = v }; filled.append(prev) }
            out[id] = Hist(dates: dates, closes: filled)
        }
        return out
    }()

    /// 자료가 언제 값인지 (현재가 시각)
    static var asOf: Date? { quotes.values.map(\.time).max() }

    /// 빌드 때 넣은 지난 종가 전부 (사이트 티커 → 종가). 사이트에서 아직 못 받았을 때만 쓴다
    static let bundled: [String: Hist] = {
        var out: [String: Hist] = [:]
        for url in Bundle.main.urls(forResourcesWithExtension: "json", subdirectory: "prices") ?? [] {
            guard let d = try? Data(contentsOf: url), let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                  let k = o["symbol"] as? String, let h = SitePrices.hist(o) else { continue }
            out[k] = h
        }
        return out
    }()
}

/// 사이트 지난 종가 (data/prices/*.json): 웹이 자산 추이·목표 진행(내 길)에 쓰는 것과 같은 파일.
/// 빌드 때 넣은 파일은 맥에서 받은 날에 멈춰 있어서, 앱은 내 종목과 원/달러만 사이트에서 다시 받아 둔다
enum SitePrices {
    /// h 키 = 사이트 티커 (KRW=X 등). asked = 그때 받으려던 티커 (사이트에 없는 종목은 h 에 없다)
    struct File: Codable { var got: Double; var updated: String; var asked: [String]; var h: [String: YahooSample.Hist] }
    static let name = "site_prices.json"
    static func cached() -> File? { Store.read(File.self, name) }

    /// 빈 값은 앞 값으로 메운다 (웹 history 와 같다)
    static func hist(_ o: [String: Any]) -> YahooSample.Hist? {
        guard let ds = o["dates"] as? [String], let c = o["close"] as? [Any], ds.count == c.count, !ds.isEmpty else { return nil }
        let raw: [Double] = c.map { ($0 as? Double) ?? .nan }
        var prev: Double = raw.first(where: { !$0.isNaN }) ?? 1
        var out: [Double] = []
        for v in raw { if !v.isNaN { prev = v }; out.append(prev) }
        return .init(dates: ds, closes: out)
    }

    private static func get(_ path: String) async -> [String: Any]? {
        var c = URLComponents(url: Config.data.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 600)))]
        var req = URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.setValue("naeilo-ios/0.1", forHTTPHeaderField: "User-Agent")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    /// Yahoo 일봉 종가 (사이트 prices 의 close 와 같은 기준). 날짜는 그 거래소 날짜
    static func daily(_ sym: String, range: String) async -> YahooSample.Hist? {
        let enc = sym.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? sym
        guard let o = try? await Relay.yahoo("/v8/finance/chart/\(enc)?range=\(range)&interval=1d", timeout: 10),
              let r = ((o["chart"] as? [String: Any])?["result"] as? [[String: Any]])?.first,
              let m = r["meta"] as? [String: Any], let ts = r["timestamp"] as? [Double],
              let cl = ((((r["indicators"] as? [String: Any])?["quote"] as? [[String: Any]])?.first)?["close"] as? [Any]) else { return nil }
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: (m["exchangeTimezoneName"] as? String) ?? "America/New_York") ?? .current; f.dateFormat = "yyyy-MM-dd"
        var ds: [String] = [], cs: [Double] = []
        for (t, c) in zip(ts, cl) {
            guard let v = num(c), v > 0 else { continue }
            let d = f.string(from: Date(timeIntervalSince1970: t))
            if ds.last == d { cs[cs.count - 1] = v } else { ds.append(d); cs.append(v) }
        }
        return ds.isEmpty ? nil : .init(dates: ds, closes: cs)
    }

    /// 한 종목: 받아 둔 3년 종가가 있으면 최근 한 달만 받아 끝을 바꾸고, 없으면 3년을 받는다
    private static func direct(_ k: String, _ had: YahooSample.Hist?) async -> YahooSample.Hist? {
        if let h = had, let last = h.dates.last, h.dates.count > 200, Day.dday(last) >= -20,
           let n = await daily(k, range: "1mo"), let first = n.dates.first {
            let keep = h.dates.firstIndex { $0 >= first } ?? h.dates.count
            let ds = Array(h.dates[..<keep]) + n.dates, cs = Array(h.closes[..<keep]) + n.closes
            let cut = max(0, ds.count - 800)   // 3년 남짓만 둔다
            return .init(dates: Array(ds[cut...]), closes: Array(cs[cut...]))
        }
        return await daily(k, range: "3y")
    }

    /// keys = 사이트 티커(= Yahoo 기호). Yahoo 에서 직접 받고, 못 받은 것만 사이트 파일로 채운다. 받은 지 minAge 안이고 다 있으면 그대로
    static func fetch(keys: Set<String>, old: File?, minAge: TimeInterval) async -> File? {
        let now = Date().timeIntervalSince1970
        if let o = old, now - o.got < minAge, keys.isSubset(of: Set(o.asked)) { return o }
        var h: [String: YahooSample.Hist] = [:]
        await withTaskGroup(of: (String, YahooSample.Hist?).self) { g in
            for k in keys { let had = old?.h[k]; g.addTask { (k, await direct(k, had)) } }
            for await (k, v) in g { if let v { h[k] = v } }
        }
        let miss = keys.filter { h[$0] == nil }
        var updated = old?.updated ?? ""
        if !miss.isEmpty, let idx = await get("index.json"), let files = idx["prices"] as? [String: String] {
            updated = idx["updated"] as? String ?? ""
            for k in miss {
                guard let f = files[k] else { continue }
                if let o = await get("prices/" + f), let x = hist(o) { h[k] = x }
            }
        }
        for k in keys where h[k] == nil { if let x = old?.h[k] { h[k] = x } }
        let out = File(got: now, updated: updated, asked: keys.sorted(), h: h)
        Store.write(out, name)
        return out
    }
}

/// 내 토스 키로 받는 지난 종가(일봉)와 원/달러. 키를 넣은 기기만 (공개 버전은 사용자 키로만 시세를 보여 준다).
/// 일봉은 수정 안 한 종가(adjusted=false): 사이트 data/prices 의 close 와 같은 기준. 받은 종목은 사이트 값 대신 쓰고, 못 받은 종목은 사이트 값 그대로.
/// 원/달러 지난 흐름(KRW=X)은 토스에 일봉이 없어 아직 사이트 값이다.
enum TossHistory {
    struct FX: Codable { var rate: Double; var prev: Double; var got: Double }   // 지금, 24시간 전, 받은 시각(초)
    /// h 키 = 사이트 티커, day = 그 종목을 마지막으로 받은 날
    struct File: Codable { var h: [String: YahooSample.Hist]; var day: [String: String]; var fx: FX? }
    static let name = "toss_history.json"
    static func cached() -> File? { TossKeys.on ? Store.read(File.self, name) : nil }

    static func merged(_ p: SitePrices.File?, _ t: File?) -> SitePrices.File? {
        guard let t, TossKeys.on, !t.h.isEmpty else { return p }
        var out = p ?? SitePrices.File(got: 0, updated: "", asked: [], h: [:])
        for (k, v) in t.h { out.h[k] = v }
        return out
    }

    private static func enc(_ v: String) -> String {
        v.addingPercentEncoding(withAllowedCharacters: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? v
    }
    /// 그 시장의 오늘 날짜와, 오늘 장이 끝났는지 (끝나기 전 오늘 일봉은 종가가 아니라 뺀다)
    private static func marketDay(kr: Bool) -> (String, Bool) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: kr ? "Asia/Seoul" : "America/New_York")!
        let c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: Date())
        let d = String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
        let m = c.hour! * 60 + c.minute!
        return (d, m >= (kr ? 15 * 60 + 40 : 16 * 60 + 10))
    }

    /// keys: 사이트 티커 → Yahoo 기호. 종목마다 하루 한 번: 처음엔 3년치(200개씩), 그다음엔 최근 30개만 받아 잇는다.
    /// 너무 많이 부르면(429)·중계에 못 닿으면 거기서 멈추고 다음 갱신 때 이어 받는다
    static func fetch(_ keys: [String: String], force: Bool) async -> File? {
        guard TossKeys.on else { return nil }
        var f = Store.read(File.self, name) ?? File(h: [:], day: [:], fx: nil)
        let now = Date().timeIntervalSince1970, today = Day.today
        let from = Day.str(Date().addingTimeInterval(-(3 * 365 + 15) * 86400))   // 사이트 파일과 같은 3년 남짓
        var miss = TossFeed.histMiss
        var stop = false
        for (k, y) in keys.sorted(by: { $0.key < $1.key }) where !stop {
            // 하루 한 번, 장이 끝난 뒤 한 번 더 (그날 종가가 정규장 종가·다음 날 전일 종가가 된다)
            let (md, closed) = marketDay(kr: y.hasSuffix(".KS") || y.hasSuffix(".KQ"))
            let stamp = closed ? today + "c" : today
            guard let c = TossFeed.code(y), (miss[c] ?? 0) < now, f.day[k] != stamp || f.h[k] == nil else { continue }
            let have = f.h[k]
            let full = (have?.dates.first ?? "9999") > Day.str(Date().addingTimeInterval(-(3 * 365 - 15) * 86400))
            var bars: [String: Double] = [:], before: String? = nil, ok = false
            for _ in 0..<(full ? 5 : 1) {
                var path = "/api/toss/v1/candles?symbol=\(enc(c))&interval=1d&adjusted=false&count=\(full ? 200 : 30)"
                if let b = before { path += "&before=" + enc(b) }
                let (code, o, e) = await TossFeed.userGet(path)
                if code == 404 { miss[c] = now + 86400; break }
                guard code == 200, let r = o?["result"] as? [String: Any] else { if e != nil || code == 429 || code == 401 || code == 403 { stop = true }; ok = false; break }
                ok = true
                var oldest = "9999"
                for v in r["candles"] as? [[String: Any]] ?? [] {
                    guard let t = v["timestamp"] as? String, t.count >= 10, let cl = num(v["closePrice"]), cl > 0 else { continue }
                    let d = String(t.prefix(10)); bars[d] = cl; oldest = min(oldest, d)
                }
                before = r["nextBefore"] as? String
                if before == nil || oldest <= from { break }
            }
            guard ok, !bars.isEmpty else { continue }
            var all: [String: Double] = full ? [:] : Dictionary(zip(have!.dates, have!.closes), uniquingKeysWith: { _, b in b })
            for (d, v) in bars { all[d] = v }
            let ds = all.keys.filter { $0 >= from && ($0 < md || ($0 == md && closed)) }.sorted()
            guard !ds.isEmpty else { continue }
            f.h[k] = .init(dates: ds, closes: ds.map { all[$0]! })
            f.day[k] = stamp
        }
        TossFeed.histMiss = miss
        // 원/달러: 10분에 한 번 (당겨 갱신하면 바로). 전일 값은 24시간 전 시각의 값
        // 일봉 받기가 막혀도(429 등) 환율은 따로 묻는다. 안 그러면 하루 묵은 환율로 평가액이 토스와 달라진다
        if force || now - (f.fx?.got ?? 0) > 600 {
            let (code, o, _) = await TossFeed.userGet("/api/toss/v1/exchange-rate")
            if code == 200, let rate = num((o?["result"] as? [String: Any])?["rate"]), rate > 0 {
                let iso = ISO8601DateFormatter(); iso.timeZone = TimeZone(identifier: "Asia/Seoul")
                let (c2, o2, _) = await TossFeed.userGet("/api/toss/v1/exchange-rate?dateTime=" + enc(iso.string(from: Date().addingTimeInterval(-86400))))
                let prev = c2 == 200 ? num((o2?["result"] as? [String: Any])?["rate"]) : nil
                f.fx = .init(rate: rate, prev: (prev ?? 0) > 0 ? prev! : (f.fx?.prev ?? rate), got: now)
            }
        }
        Store.write(f, name)
        return f
    }
}

/// 매매기준율: 사이트가 하루 한 번 저장하는 data/mar.json ({"USD": {"rate", "date"}}). 웹 평가액과 같은 값
enum MarFeed {
    struct Prev: Codable { var rate: Double; var date: String }
    /// prev = 바로 앞 날짜 고시 (새 고시는 전날 오후에 나오지만 토스는 그날 오전 8시 59분부터 쓴다)
    struct File: Codable { var rate: Double; var date: String; var got: Double; var prev: Prev? = nil }
    static let name = "site_mar.json"
    /// 그 날짜 고시를 쓰기 시작했는지: 한국 시간 그날 오전 8시 59분부터
    static func started(_ date: String) -> Bool {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "Asia/Seoul"); f.dateFormat = "yyyy-MM-dd HH:mm"
        return f.string(from: Date()) >= date + " 08:59"
    }
    static func cached() -> File? { Store.read(File.self, name) }

    /// 받은 지 minAge 초가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값
    static func fetch(minAge: TimeInterval = 0) async -> File? {
        let old = cached()
        // 오늘 고시가 아닌 값은 10분만 믿는다 (앱을 새로 깔거나 빌드한 직후 며칠 묵은 값을 30분 넘게 쓰지 않게)
        // 단, 오전 8시 59분 전인데 앞 고시가 없으면 기다리지 않고 바로 받는다 (앞 고시를 써야 하니까)
        if let o = old, MarFeed.started(o.date) || o.prev != nil, Date().timeIntervalSince1970 - o.got < (o.date >= Day.today ? minAge : min(minAge, 600)) { return o }
        // 1순위: 서울외국환중개에서 직접 (최신 고시 + 앞 날짜 고시). 못 받을 때만 웹(data/mar.json → 중계 /mar)
        var best: File? = await smbs()
        if best == nil {
            var c = URLComponents(url: Config.data.appendingPathComponent("mar.json"), resolvingAgainstBaseURL: false)!
            c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 600)))]
            best = await get(URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10))
            // 웹 marFetch 와 같게: 저장소 값이 오늘 고시가 아니면 중계(/mar)에서 한 번 더 (더 새 날짜일 때만 바꾼다)
            let have = max(best?.date ?? "", old?.date ?? "")
            if have < Day.today, let x = await get(URLRequest(url: Config.relay.appendingPathComponent("mar"), timeoutInterval: 10)), x.date > (best?.date ?? "") { best = x }
        }
        guard var f = best else { return old }
        if let o = old, o.date > f.date { f = File(rate: o.rate, date: o.date, got: f.got, prev: o.prev) }   // 중계에서 받아 둔 더 새 값은 지킨다
        else if let o = old, o.date < f.date, f.prev == nil { f.prev = Prev(rate: o.rate, date: o.date) }   // 날짜가 바뀌면 앞 고시를 남긴다
        else if let o = old, f.prev == nil { f.prev = o.prev }
        Store.write(f, name)
        return f
    }
    /// 서울외국환중개 매매기준율 표 (최근 2주): 마지막 = 최신 고시, 그 앞 = prev. 중계 worker·server.py 와 같은 파싱
    private static func smbs() async -> File? {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "Asia/Seoul"); f.dateFormat = "yyyy-MM-dd"
        let st = f.string(from: Date().addingTimeInterval(-14 * 86400)), end = f.string(from: Date())
        guard let url = URL(string: "http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_\(st)_\(end)") else { return nil }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
        req.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        req.setValue("http://www.smbs.biz/ExRate/StdExRate.jsp", forHTTPHeaderField: "Referer")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let t = String(data: data, encoding: .isoLatin1),
              let re = try? NSRegularExpression(pattern: "label=['\"]([^'\"]+)['\"][^>]*?value=['\"]([\\d,.]+)['\"]") else { return nil }
        var days: [Prev] = []
        for m in re.matches(in: t, range: NSRange(t.startIndex..., in: t)) {
            guard let lr = Range(m.range(at: 1), in: t), let vr = Range(m.range(at: 2), in: t),
                  let rate = Double(t[vr].replacingOccurrences(of: ",", with: "")), rate > 500, rate < 5000 else { continue }
            let n = t[lr].split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
            guard n.count >= 3 else { continue }
            days.append(Prev(rate: rate, date: String(format: "%04d-%02d-%02d", n[0] < 100 ? 2000 + n[0] : n[0], n[1], n[2])))
        }
        days.sort { $0.date < $1.date }
        guard let last = days.last else { return nil }
        let pv = days.dropLast().last
        return File(rate: last.rate, date: last.date, got: Date().timeIntervalSince1970, prev: pv)
    }
    private static func get(_ req: URLRequest) async -> File? {
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let u = o["USD"] as? [String: Any], let rate = num(u["rate"]), rate > 0, let date = u["date"] as? String else { return nil }
        // 사이트·중계가 앞 날짜 고시도 주면 같이 둔다 (앱을 처음 깔았거나 저녁에 새 고시만 받은 때도 오전 8시 59분 전엔 앞 고시)
        let pv = (o["USD_prev"] as? [String: Any]).flatMap { p in num(p["rate"]).flatMap { r in (p["date"] as? String).map { Prev(rate: r, date: $0) } } }
        return File(rate: rate, date: date, got: Date().timeIntervalSince1970, prev: pv.flatMap { $0.rate > 0 && $0.date < date ? $0 : nil })
    }
}
