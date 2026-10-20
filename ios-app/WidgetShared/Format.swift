import Foundation

/// 위젯 문구의 영어. 기기 언어가 한국어가 아니면 lines_en.json 의 ui 표(한국어 문구 → 영어)에서 찾는다 (tools/lines.py 가 만든다).
/// 한국어 기기이거나 표에 없으면 한국어 그대로. {n} 같은 자리는 vars 로 채운다 (예: WL.t("{n}번째 블록", ["n": "3"]))
enum WL {
    /// 서재와 같은 규칙: 기기 첫 언어가 한국어가 아니면 영어
    /// 영어 여부. 앱은 자기 언어(iOS 앱별 언어 포함), 위젯은 앱이 App Group 에 적어 둔 언어를 따른다
    /// (위젯 확장은 앱별 언어 설정을 못 봐서 폰 언어를 따르기 때문)
    static let en: Bool = {
        if Bundle.main.bundleURL.pathExtension == "appex", let l = Store.defaults.string(forKey: "appLang") { return l == "en" }
        return Bundle.main.preferredLocalizations.first == "en"
    }()
    private struct File: Decodable { let ui: [String: String] }
    private static let ui: [String: String] = {
        guard en, let url = Bundle.main.url(forResource: "lines_en", withExtension: "json"),
              let d = try? Data(contentsOf: url), let f = try? JSONDecoder().decode(File.self, from: d) else { return [:] }
        return f.ui
    }()
    static func t(_ ko: String, _ vars: [String: String] = [:]) -> String {
        var s = en ? (ui[ko] ?? ko) : ko
        for (k, v) in vars { s = s.replacingOccurrences(of: "{\(k)}", with: v) }
        return s
    }
    /// 등락 방향 한 낱말: 상승·하락 (영어 기기면 up·down)
    static func dir(_ x: Double) -> String { t(x >= 0 ? "상승" : "하락") }
}

enum Fmt {
    /// 5.27억
    /// 1억이 안 되면 만원으로 (4,002만)
    static func eok(_ v: Double, _ digits: Int = 2) -> String {
        if WL.en { return krwC(v) }
        return abs(v) < 1e8 ? man(v) : (v < 0 ? "−" : "") + String(format: "%.\(digits)f억", abs(v) / 1e8)
    }
    /// 영어: 만·억 대신 ₩2.3M 꼴 (앱 AppModel.krwC 와 같은 규칙)
    static func krwC(_ krw: Double) -> String {
        let a = abs(krw), u: [(Double, String)] = [(1e12, "T"), (1e9, "B"), (1e6, "M"), (1e3, "K")]
        let s = u.first { a >= $0.0 }.map { (a / $0.0).formatted(.number.precision(.significantDigits(1...3))) + $0.1 } ?? Int(a.rounded()).formatted()
        return (krw < 0 ? "−" : "") + "₩" + s
    }
    /// 499만
    static func man(_ v: Double) -> String {
        if WL.en { return krwC(abs(v)) }
        return (dec.string(from: NSNumber(value: Int((abs(v) / 1e4).rounded()))) ?? "0") + "만"
    }
    // 서식기는 만들 때 비싸서 한 번만 만들어 둔다
    private static let dec: NumberFormatter = { let n = NumberFormatter(); n.numberStyle = .decimal; return n }()
    private static let hm: DateFormatter = { let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "HH:mm"; return f }()
    private static let mdhm: DateFormatter = { let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M/d HH:mm"; return f }()
    /// +1.8%
    static func pct(_ x: Double, _ d: Int = 1) -> String { (x > 0 ? "+" : x < 0 ? "−" : "") + String(format: "%.\(d)f%%", abs(x) * 100) }
    static func arrow(_ x: Double) -> String { x >= 0 ? "▲" : "▼" }
    static func comma(_ v: Int) -> String { dec.string(from: NSNumber(value: v)) ?? "\(v)" }
    static func time(_ d: Date?) -> String {
        guard let d else { return "-" }
        return (Calendar.current.isDateInToday(d) ? hm : mdhm).string(from: d)
    }
}

/// 미국장(프리마켓~애프터마켓) 시간: 한국 시간 평일 오후 5시 ~ 다음 날 오전 9시. 사이트 시세 수집과 같은 범위
enum MarketHours {
    static func isActive(_ d: Date = Date()) -> Bool {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "UTC")!
        let wd = c.component(.weekday, from: d), h = c.component(.hour, from: d) // 일=1 … 토=7
        if wd == 1 || wd == 7 { return wd == 7 && h == 0 } // 토요일 0시 UTC 는 금요일 장 마감 직후
        return h >= 8 || (wd != 2 && h == 0)
    }
    /// 한국 장 (평일 KST 9:00~16:00 = UTC 0~7시). 내부 테스트 국내 시세 종목이 있을 때만 갱신 시간에 넣는다
    static func isKR(_ d: Date = Date()) -> Bool {
        var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(identifier: "UTC")!
        let wd = c.component(.weekday, from: d), h = c.component(.hour, from: d)
        return wd >= 2 && wd <= 6 && h < 7
    }
    /// 다음 갱신 시각: 장중엔 설정한 주기, 장 밖엔 3시간 (그 사이 장이 열리면 그때)
    static func nextRefresh(_ now: Date = Date(), kr: Bool = false) -> Date {
        let iv = TimeInterval(Store.intervalMin * 60)
        let open = { (d: Date) in isActive(d) || (kr && isKR(d)) }
        if open(now) { return now.addingTimeInterval(iv) }
        var t = now
        for _ in 0..<36 { t = t.addingTimeInterval(300); if open(t) { return t.addingTimeInterval(120) } }
        return now.addingTimeInterval(3 * 3600)
    }
}

/// 위젯 정기 갱신 (기본·잠금화면·인물 위젯이 같이 쓴다): 지금 가격·환율을 위젯이 직접 받고 다음 갱신 시각을 준다.
/// 숫자는 naeilo 앱이 계산해서 App Group 폴더에 써 둔다. 10분 안에 받았으면 다시 받지 않는다 (위젯이 여럿이어도 요청이 적다)
enum WidgetRefresh {
    static func fetch() async -> Date {
        let before = Store.newestGot
        defer { if Store.newestGot > before { Store.fetchFailed = false } }
        await QuoteFeed.fetch(minAge: 600)
        // 내부 테스트 국내 시세 종목이 있으면 그것도 (한국 장중에도 갱신)
        let feed = Store.read(WFeed.self, "feed.json")
        let ky = feed?.ky ?? [:]
        await KrQuoteFeed.fetch(ky, minAge: 600)
        // 내 토스 키가 있으면 토스 지금 가격도 위젯이 직접 (앱을 안 열어도 앱과 같은 값). 국내 종목이 있으면 한국 장중에도 갱신
        let toss = await TossFeed.widgetFetch(minAge: 600)
        // 내 Alpaca 키가 있으면 미국 종목 지금 가격도 위젯이 직접
        await AlpacaFeed.widgetFetch(minAge: 600)
        // 내 Longbridge 키가 있으면 그것도
        await LongbridgeFeed.widgetFetch(minAge: 600)
        let tossKr = toss != nil && (feed?.ty ?? [:]).values.contains { $0.hasSuffix(".KS") || $0.hasSuffix(".KQ") }
        return untilTrialEnd(MarketHours.nextRefresh(kr: !ky.isEmpty || tossKr))
    }

    /// 7일 무료가 다음 갱신보다 먼저 끝나면 그때 한 번 더 그려 바로 잠근다 (전에는 다음 갱신까지, 장 밖이면 최대 3시간 열려 있었다)
    static func untilTrialEnd(_ next: Date) -> Date {
        let end = Store.trialEnd
        guard !Store.subOn, end > Date().timeIntervalSince1970 else { return next }
        return min(next, Date(timeIntervalSince1970: end))
    }
}

/// 위젯 아래 줄의 '3분 전 · 토스'. 지금은 그 위젯 그림의 시각(타임라인 항목 시각)으로 센다
enum Fresh {
    static func ago(_ d: Date?, now: Date) -> String {
        guard let d else { return "-" }
        let m = Int(now.timeIntervalSince(d) / 60)
        if m < 1 { return WL.t("방금") }
        if m < 60 { return WL.t("{n}분 전", ["n": "\(m)"]) }
        if m < 24 * 60 { return WL.t("{n}시간 전", ["n": "\(m / 60)"]) }
        return Fmt.time(d)
    }
    /// 출처 이름 (Engine.build 의 tag). Alpaca 는 그대로
    static func src(_ feed: String?) -> String? { feed.map { $0 == "Alpaca" ? $0 : WL.t($0) } }
    /// '3분 전 · 토스', 마지막 ↻ 가 실패했으면 '받기 실패 · 3분 전 · 토스'
    static func label(_ at: Date?, _ feed: String?, now: Date) -> String {
        var parts = [ago(at, now: now)]
        if let s = src(feed) { parts.append(s) }
        if Store.fetchFailed { parts.insert(WL.t("받기 실패"), at: 0) }
        return parts.joined(separator: " · ")
    }
    /// 타임라인 항목 시각들: 지금부터 다음 갱신 전까지, '몇 분 전'이 바뀔 즈음마다 다시 그린다
    static func dates(_ now: Date, until next: Date) -> [Date] {
        let mins: [Double] = [0, 1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180]
        return mins.map { now.addingTimeInterval($0 * 60) }.filter { $0 == now || $0 < next }
    }
}
