import Foundation

// 종목 탭 '내 종목 합산' 그래프: 토스증권 날짜별 합산과 같은 줄 (docs/decisions.md 2026-10-29)
// 줄 D = 한국 D+1일 미국 장 마감 순간(05:00, 서머타임 끝나면 06:00)의 평가금. D 는 달력 날짜(주말 포함)
//   국내: 한국 D일 이하 마지막 종가 × 그날 수량
//   미국: 미국 D일 이하 마지막 정규장 종가 × 그날 수량 × 매매기준율(고시 날짜 ≤ D 중 마지막)
// 그래서 주말 줄은 금요일 줄과 원 단위까지 같다. 맨 끝은 지금 평가액(홈 평가액)
//
// 계산(line·points)이 읽는 입력은 아래뿐이다. 기기 시계·스위치 등 다른 입력을 읽지 않는다
//   items  종목별: 사이트 티커, 미국 종목인가, 날짜별 종가(PriceHistory, 국내 주식 합계는 krLog), 첫 기록 전 0인가, 기록이 없을 때 쓸 값, 지금 수량
//   lots   날짜별 수량 기록(Lots). 가장 이른 기록 전 날짜는 첫 기록 수량, 기록이 없으면 지금 수량
//   mar    날짜별 매매기준율(MarDaily). 가장 이른 고시 전 날짜는 가장 이른 값
//   last   마지막으로 끝난 줄의 날짜(lastLine), now 지금 평가액
enum TossSum {
    struct Item {
        let key: String              // 사이트 티커 (Lots 칸 이름)
        let usd: Bool
        let dates: [String]          // 오름차순 "yyyy-MM-dd" (그 시장 날짜)
        let closes: [Double]
        let zeroBefore: Bool         // 국내 주식 합계: 첫 기록 전은 0
        let flat: Double             // 과거 시세가 없을 때 쓰는 값 (평평하게)
        let qty: Double              // 지금 수량 (수량 기록이 없을 때)
    }
    struct Point { let date: String?; let value: Double }   // date nil = 지금

    /// 날짜별 매매기준율 (고시 날짜 오름차순)
    struct Rates {
        let d: [String], c: [Double]
        func at(_ day: String) -> Double {
            guard !c.isEmpty else { return 0 }
            let k = TossSum.idx(d, day)
            return c[max(0, k)]
        }
    }

    /// 날짜 목록에서 day 이하 마지막 자리 (없으면 -1)
    static func idx(_ ds: [String], _ day: String) -> Int {
        var lo = 0, hi = ds.count - 1, k = -1
        while lo <= hi { let mid = (lo + hi) / 2; if ds[mid] <= day { k = mid; lo = mid + 1 } else { hi = mid - 1 } }
        return k
    }

    static func price(_ x: Item, _ day: String) -> Double {
        guard !x.closes.isEmpty, x.dates.count == x.closes.count else { return x.flat }
        let k = idx(x.dates, day)
        return k >= 0 ? x.closes[k] : (x.zeroBefore ? 0 : x.closes[0])
    }

    /// 줄 D (원). 수량은 그날 기록, 가장 이른 기록 전이면 첫 기록, 기록이 없으면 지금 수량
    static func line(_ items: [Item], lots: [Lot], mar: Rates, _ day: String) -> Double {
        let fx = mar.at(day), h = Lots.on(lots, day) ?? lots.first?.h
        return items.reduce(0) { s, x in
            let v = price(x, day) * (h.map { $0[x.key] ?? 0 } ?? x.qty)
            return s + (x.usd ? v * fx : v)
        }
    }

    /// 기간별 점: 날마다 줄(1일 = 어제 줄, 1주 = 7줄, 1·3개월 = 그 기간 날마다), 6개월부터는 주(월~일)마다 마지막 날 줄. 끝에 지금
    static func points(_ items: [Item], lots: [Lot], mar: Rates, period: Period, last: String, now: Double) -> [Point] {
        let end = MC.dayNum(last), from = end - period.days + 1
        var days: [Int] = []
        for n in from...end {
            if period.weekly, let p = days.last, (p + 3) / 7 == (n + 3) / 7 { days[days.count - 1] = n } else { days.append(n) }   // 1970-01-01 = 목, 월요일에 시작하는 주
        }
        return days.map { n in let d = MC.dayStr(n); return Point(date: d, value: line(items, lots: lots, mar: mar, d)) } + [Point(date: nil, value: now)]
    }

    /// 마지막으로 끝난 줄의 날짜: 뉴욕 오후 4시(정규장 마감)가 지난 가장 늦은 미국 날짜
    static func lastLine(_ t: Date) -> String {
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "America/New_York")!
        let c = cal.dateComponents([.year, .month, .day, .hour], from: t)
        let n = MC.dayNum(String(format: "%04d-%02d-%02d", c.year ?? 1970, c.month ?? 1, c.day ?? 1))
        return MC.dayStr((c.hour ?? 0) >= 16 ? n : n - 1)
    }
}

extension AppModel {
    /// 종목 탭 합산 그래프 입력: 지금 종목 + 수량 기록에만 남은(판) 종목. 평가에서 빠지는 종목(Market.needsKey)·가상 종목은 뺀다
    func tossSumItems() -> [TossSum.Item] {
        var out: [TossSum.Item] = [], seen = Set<String>()
        func add(_ s: Symbol, _ q: Double) {
            let key = Sync.siteTicker(s.id)
            guard !seen.contains(key), !Market.shared.needsKey(s) else { return }
            seen.insert(key)
            if s.id == KoreaEstimate.sumId {
                let L = CustomSymbols.shared.krLog
                out.append(.init(key: key, usd: false, dates: L.map(\.d), closes: L.map(\.v), zeroBefore: true, flat: s.close, qty: q))
            } else {
                let h = Sample.isVirtual(s.id) ? nil : PriceHistory.of(s.id)
                out.append(.init(key: key, usd: s.currency == .usd, dates: h?.dates ?? [], closes: h?.closes ?? [], zeroBefore: false, flat: s.close, qty: q))
            }
        }
        for r in rows { add(r.sym, r.h.qty) }
        for k in Set(lots.flatMap { $0.h.keys }) where !seen.contains(k) {
            if let id = Sync.appId(k), let s = Sample.symbol(id) { add(s, 0) }
        }
        return out
    }
}

/// 날짜별 매매기준율 3년 (서울외국환중개). 서울외국환중개에서 직접 받고, 못 받으면 사이트 data/mar_daily.json ({"src","updated","d","c"}, 사이트 자료를 쓰는 내부 베타만). 하루 한 번
enum MarDaily {
    struct File: Codable { var got: Double? = nil; var d: [String]; var c: [Double] }   // got = 이 기기가 받은 때 (사이트 파일엔 없다)
    static let name = "mar_daily.json"
    static func cached() -> File? { Store.read(File.self, name) }

    /// 저장해 둔 값 + 지금 쓰는 고시(MarFeed 의 오늘·앞 고시)를 합친 표
    static func rates(_ f: File?, _ m: MarFeed.File?) -> TossSum.Rates {
        var map: [String: Double] = [:]
        if let f { for (d, c) in zip(f.d, f.c) where c > 0 { map[d] = c } }
        if let p = m?.prev, p.rate > 0 { map[p.date] = p.rate }
        if let m, m.rate > 0 { map[m.date] = m.rate }
        let ds = map.keys.sorted()
        return .init(d: ds, c: ds.map { map[$0]! })
    }

    /// 받은 지 하루가 안 됐으면 저장해 둔 값. 실패하면 저장해 둔 값
    static func fetch() async -> File? {
        let old = cached()
        if let o = old, Date().timeIntervalSince1970 - (o.got ?? 0) < 86400 { return o }
        var f: File? = await smbs()
        if f == nil, Config.usesCollectedData {
            var c = URLComponents(url: Config.data.appendingPathComponent(name), resolvingAgainstBaseURL: false)!
            c.queryItems = [URLQueryItem(name: "t", value: String(Int(Date().timeIntervalSince1970 / 3600)))]
            if let (data, resp) = try? await URLSession.shared.data(for: URLRequest(url: c.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)),
               (resp as? HTTPURLResponse)?.statusCode == 200, let x = try? JSONDecoder().decode(File.self, from: data), x.d.count == x.c.count, !x.d.isEmpty { f = x }
        }
        guard var x = f else { return old }
        x.got = Date().timeIntervalSince1970
        Store.write(x, name)
        return x
    }

    /// 서울외국환중개 매매기준율 표 3년 (MarFeed.smbs 와 같은 파싱, 두 자리 연도)
    private static func smbs() async -> File? {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "Asia/Seoul"); f.dateFormat = "yyyy-MM-dd"
        let st = f.string(from: Date().addingTimeInterval(-1096 * 86400)), end = f.string(from: Date())
        guard let url = URL(string: "http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_\(st)_\(end)") else { return nil }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        req.setValue("Mozilla/5.0", forHTTPHeaderField: "User-Agent")
        req.setValue("http://www.smbs.biz/ExRate/StdExRate.jsp", forHTTPHeaderField: "Referer")
        guard let (data, resp) = try? await URLSession.shared.data(for: req), (resp as? HTTPURLResponse)?.statusCode == 200,
              let t = String(data: data, encoding: .isoLatin1),
              let re = try? NSRegularExpression(pattern: "label=['\"]([^'\"]+)['\"][^>]*?value=['\"]([\\d,.]+)['\"]") else { return nil }
        var map: [String: Double] = [:]
        for m in re.matches(in: t, range: NSRange(t.startIndex..., in: t)) {
            guard let lr = Range(m.range(at: 1), in: t), let vr = Range(m.range(at: 2), in: t),
                  let rate = Double(t[vr].replacingOccurrences(of: ",", with: "")), rate > 500, rate < 5000 else { continue }
            let n = t[lr].split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
            guard n.count >= 3 else { continue }
            map[String(format: "%04d-%02d-%02d", n[0] < 100 ? 2000 + n[0] : n[0], n[1], n[2])] = rate
        }
        guard !map.isEmpty else { return nil }
        let ds = map.keys.sorted()
        return File(d: ds, c: ds.map { map[$0]! })
    }
}
