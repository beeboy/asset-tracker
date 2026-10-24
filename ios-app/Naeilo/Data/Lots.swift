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
