import Foundation

// naeilo를 쓴 날부터의 내 기록: 하루 한 줄 (사이트 web/app.js recordDay 와 같은 모양·같은 계산).
// d 날짜, v 평가액(원), c 넣은 돈(원), q {사이트 티커: [수량, 원화 주가]}, f {사이트 티커: [산·판 수량, 원화 금액]}
// 넣은 돈 = 전날 넣은 돈 + 그날 '더 샀어요·팔았어요' 금액(그때 가격) + 그 밖에 바뀐 수량(직접 고치기·추가·삭제)×그날 가격. 첫날은 그날 평가액.
// 이 폰에만 쌓이고 '내 폰에 저장하기' 파일과 기기 동기화(daily 칸)에 같이 들어간다.
struct DayRec: Codable, Equatable {
    var d: String
    var v: Double
    var c: Double
    var q: [String: [Double]]
    var f: [String: [Double]]? = nil
}

enum MyRecord {
    static let key = "myRecord", cap = 1200

    static func load() -> [DayRec] {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode([DayRec].self, from: $0) } ?? []
    }
    static func save(_ d: [DayRec]) {
        if let data = try? JSONEncoder().encode(d) { UserDefaults.standard.set(data, forKey: key) }
    }

    /// 오늘 줄을 새로 쓰거나 고친다. q = 지금 보유 {티커: [수량, 원화 주가]}, trade = 방금 저장한 매매 (티커, 수량 ±, 원화 금액 ±)
    static func update(_ D: inout [DayRec], q: [String: [Double]], trade: (String, Double, Double)?, today: String = Day.today) {
        if let l = D.last, l.d > today { return }           // 기기 시계가 거꾸로 가면 건드리지 않는다
        let cur = D.last?.d == today ? D.last : nil
        let back = cur == nil ? 1 : 2
        let prev: DayRec? = D.count >= back ? D[D.count - back] : nil
        if prev == nil && q.isEmpty { return }
        var f = cur?.f ?? [:]
        if let tr = trade { let a = f[tr.0] ?? [0, 0]; f[tr.0] = [a[0] + tr.1, a[1] + tr.2] }
        let pq = prev?.q ?? [:]
        var c = prev?.c ?? 0, v = 0.0
        for t in Set(q.keys).union(pq.keys).union(f.keys) {
            let now = q[t]?[0] ?? 0, fl = f[t] ?? [0, 0]
            let px = q[t]?[1] ?? pq[t]?[1] ?? (fl[0] != 0 ? fl[1] / fl[0] : 0)
            c += fl[1] + (now - (pq[t]?[0] ?? 0) - fl[0]) * px
            v += now * (q[t]?[1] ?? 0)
        }
        let rec = DayRec(d: today, v: v.rounded(), c: c.rounded(), q: q, f: f.isEmpty ? nil : f)
        if cur != nil { D[D.count - 1] = rec } else { D.append(rec) }
        if D.count > cap { D.removeFirst(D.count - cap) }
    }

    /// 동기화: 날짜별로 합친다 (같은 날은 받은 쪽)
    static func merge(_ mine: [DayRec], _ remote: [DayRec]) -> [DayRec] {
        var m: [String: DayRec] = [:]
        for x in mine { m[x.d] = x }
        for x in remote { m[x.d] = x }
        return Array(m.values.sorted { $0.d < $1.d }.suffix(cap))
    }
    static func fromJSON(_ any: Any?) -> [DayRec] {
        guard let any, let data = try? JSONSerialization.data(withJSONObject: any) else { return [] }
        return (try? JSONDecoder().decode([DayRec].self, from: data)) ?? []
    }
    static func toJSON(_ d: [DayRec]) -> Any? {
        (try? JSONEncoder().encode(d)).flatMap { try? JSONSerialization.jsonObject(with: $0) }
    }
}

extension AppModel {
    /// 지금 보유로 오늘 기록을 고친다. 샘플(DRNK·QQQ)이면 남기지 않는다. 앱 시작·앞으로 올 때·홈을 열 때·시세를 받을 때·수량이 바뀔 때
    /// 가격을 아직 못 받은 종목은 매수 단가로 어림하고, 종목 정보도 없으면 그 종목만 뺀다 (한 종목 때문에 하루가 통째로 빠지지 않게)
    func recordDay(trade: (String, Double, Double)? = nil) {
        guard !isSample, persists else { return }   // 시안 확인용 실행에서는 남기지 않는다
        recordLot()     // 수량 기록 (바뀐 날만)
        var q: [String: [Double]] = [:]
        for h in holdings where h.qty > 0 {
            guard let s = Sample.symbol(h.symbol) else { continue }
            let px = s.last > 0 ? s.last : h.avg
            guard px > 0 else { continue }
            q[Sync.siteTicker(h.symbol)] = [h.qty, (krw(s, px) * 100).rounded() / 100]
        }
        var d = myRecord
        MyRecord.update(&d, q: q, trade: trade)
        if d != myRecord { myRecord = d }
    }
}
