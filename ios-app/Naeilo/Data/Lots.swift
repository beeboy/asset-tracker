import Foundation

// 수량이 바뀐 날만 남기는 기록 (사이트 web/app.js recordLot 과 같은 모양·같은 규칙). 내 길의 실제 평가액을 그날 수량으로 계산한다.
// d 날짜, h {사이트 티커: 수량}. 기기 동기화에서는 사이트 입력값의 lots 칸에 날짜별로 합친다.
struct Lot: Codable, Equatable {
    var d: String
    var h: [String: Double]
}

enum Lots {
    static let key = "lots", cap = 400

    static func load() -> [Lot] {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode([Lot].self, from: $0) } ?? []
    }
    static func save(_ l: [Lot]) {
        if let data = try? JSONEncoder().encode(l) { UserDefaults.standard.set(data, forKey: key) }
    }

    /// 웹 recordLot: 마지막 기록과 수량이 다르면 오늘 줄(같은 날이면 고친다). 첫 줄은 내 길 시작일이 더 이르면 그 날짜로
    static func record(_ L: inout [Lot], h: [String: Double], start: String?, today: String = Day.today) {
        if let last = L.last {
            if last.h == h || last.d > today { return }      // 같거나, 기기 시계가 거꾸로 가면 그대로
            if last.d == today { L[L.count - 1].h = h } else { L.append(Lot(d: today, h: h)) }
        } else {
            L.append(Lot(d: start.map { min($0, today) } ?? today, h: h))
        }
        if L.count > cap { L.removeFirst(L.count - cap) }
    }

    /// 날짜 d 에 들고 있던 수량 (그날 또는 그 전 마지막 기록). 기록이 없으면 nil
    static func on(_ L: [Lot], _ d: String) -> [String: Double]? { L.last { $0.d <= d }?.h }

    /// 동기화: 지난 날은 사이트 기록만 쓰고 (앱에만 남은 예전 줄이 웹과 다른 시작값을 만들지 않게), 오늘 줄은 이 기기 값. 사이트에 기록이 없으면 이 기기 기록
    static func withSite(_ site: [Lot], _ mine: [Lot]) -> [Lot] {
        site.isEmpty ? mine : merge(site, mine.filter { $0.d >= Day.today })
    }
    /// 동기화: 날짜별로 합친다 (같은 날은 받은 쪽)
    static func merge(_ mine: [Lot], _ remote: [Lot]) -> [Lot] {
        var m: [String: Lot] = [:]
        for x in mine { m[x.d] = x }
        for x in remote { m[x.d] = x }
        return Array(m.values.sorted { $0.d < $1.d }.suffix(cap))
    }
    static func fromJSON(_ any: Any?) -> [Lot] {
        guard let any, JSONSerialization.isValidJSONObject(any), let data = try? JSONSerialization.data(withJSONObject: any) else { return [] }
        return (try? JSONDecoder().decode([Lot].self, from: data)) ?? []
    }
    static func toJSON(_ l: [Lot]) -> Any? {
        (try? JSONEncoder().encode(l)).flatMap { try? JSONSerialization.jsonObject(with: $0) }
    }
}

extension YahooSample.Hist {
    /// 날짜 d 의 종가 (그날이 없으면 그 전 마지막 거래일). d 가 첫날보다 앞이면 nil
    func on(_ d: String) -> Double? {
        var lo = 0, hi = dates.count - 1, k = -1
        while lo <= hi { let mid = (lo + hi) / 2; if dates[mid] <= d { k = mid; lo = mid + 1 } else { hi = mid - 1 } }
        return k >= 0 && k < closes.count ? closes[k] : nil
    }
}

extension AppModel {
    /// 지금 수량을 수량 기록에 남긴다 (바뀐 날만). 샘플·시안 실행에서는 남기지 않는다
    func recordLot() {
        guard !isSample, persists else { return }
        var h: [String: Double] = [:]
        for x in holdings where x.qty > 0 && !Sample.isVirtual(x.symbol) { h[Sync.siteTicker(x.symbol)] = x.qty }
        guard !h.isEmpty else { return }
        var L = lots
        Lots.record(&L, h: h, start: Day.str(pathStartDate))
        if L != lots { lots = L }
    }
}

extension AppModel {
    /// 오늘 손익(원화)과 등락: 어제 수량 × 전일 종가에서 지금까지. 오늘 더 사고판 것도 넣는다
    /// 손익 = 지금 평가액 − 어제 수량 × 전일 종가 − 오늘 산 돈 + 오늘 판 돈. 산·판 돈은 내 기록(그때 가격), 그 밖에 늘어난 수량(종목 추가·직접 고치기)은 구매가(평균 단가)
    /// 다 판 종목은 내 종목에서 빠져 여기 오지 않는다
    func todayPL(_ r: Row) -> (w: Double, d: Double) {
        let s = r.sym, t = Sync.siteTicker(r.h.symbol)
        guard !isSample, !Sample.isVirtual(r.h.symbol), s.last > 0 else { return (krw(s, r.h.qty * s.quote.prevClose * s.quote.change), s.quote.change) }
        let q0 = lots.last { $0.d < Day.today }?.h[t] ?? 0                       // 어제 수량
        let f = myRecord.last.flatMap { $0.d == Day.today ? $0.f?[t] : nil } ?? [0, 0]   // 오늘 산·판 [수량 ±, 원화 금액 ±]
        let dx = r.h.qty - q0 - f[0]                                               // 기록 없이 바뀐 수량
        let w = krw(s, r.h.qty * s.last - q0 * s.quote.prevClose - dx * r.h.avg) - f[1]
        let base = krw(s, q0 * s.quote.prevClose + max(0, dx) * r.h.avg) + max(0, f[1])
        return (w, base > 0 ? w / base : 0)
    }
}
