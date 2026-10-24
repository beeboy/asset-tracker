import SwiftUI

// 임시 진단 (내부 베타만): 보유 미국 종목마다 앱이 고른 가격과 출처, 출처별 값·시각을 보인다.
// 아이폰 평가액이 토스·안드로이드와 다른 까닭을 기기에서 보려고 넣었다. 원인을 잡으면 지운다. 키 값은 보이지 않는다.
struct PriceDiagView: View {
    @Environment(AppModel.self) private var m

    var body: some View {
        Card {
            Text("임시 진단: 미국 종목 시세 출처").appFont(15, .bold)
            Text("지금 \(Self.et(Date().timeIntervalSince1970)) · 장 닫힘 \(USSession.closed() ? "예" : "아니오")")
                .appFont(12).foregroundStyle(Theme.sub)
            ForEach(m.holdings, id: \.symbol) { h in
                if let s = Sample.symbol(h.symbol), s.currency == .usd {
                    Text(Self.line(s)).appFont(12).monospaced().foregroundStyle(Theme.sub)
                        .fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
                }
            }
        }
    }

    /// 미국 동부 시각 (요일 포함)
    static func et(_ t: Double) -> String {
        guard t > 0 else { return "-" }
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "America/New_York"); f.dateFormat = "EEE MM-dd HH:mm:ss"
        return f.string(from: Date(timeIntervalSince1970: t)) + " ET"
    }

    /// 반올림 없이 받은 그대로 (Double 원래 값)
    private static func n(_ v: Double?) -> String { v.map { "\($0)" } ?? "-" }

    static func line(_ s: Symbol) -> String {
        let mk = Market.shared
        let q = mk.quote(s)
        let src = mk.isToss(s) ? "toss" : mk.isAlpaca(s) ? "alpaca" : mk.isLongbridge(s) ? "longbridge" : mk.site != nil ? "site" : "sample/close"
        let key = YahooSample.keyFor[s.id] ?? CustomSymbols.yahoo(for: s)
        let hist = mk.tossHist?.h[SiteStats.key(s.id)].map { TossDaily.Hist(dates: $0.dates, closes: $0.closes) }
        var r = "\(s.id)  앱 \(n(q.last)) [\(src)]  전일 \(n(q.prevClose))"
        if let t = mk.toss?.q[s.id] {
            r += "\n 토스 \(n(t.last)) @ \(et(t.at))  주간거래 \(TossDaily.daytime(t.at) ? "예" : "아니오")"
            r += "\n 토스 일봉 정규장 \(n(TossDaily.reg(hist, at: t.at)))  마지막 일봉 \(hist?.dates.last ?? "-") \(n(hist?.closes.last))"
        } else { r += "\n 토스 없음" }
        if let a = mk.alpaca?.q[s.id] { r += "\n Alpaca \(n(a.last)) @ \(et(a.at))" }
        if let l = mk.longbridge?.q[s.id] { r += "\n Longbridge \(n(l.last)) @ \(et(l.at))" }
        if let w = mk.site?.q[key] { r += "\n 사이트 \(n(w.last)) 정규장 \(n(w.reg)) @ \(et(w.at))" } else { r += "\n 사이트 없음" }
        return r
    }
}
