import Foundation

// 인물 대사 데이터: Resources/lines_ko.json (tools/lines.py 가 만든다, 146개 상황 × 인물별 최대 100줄).
// 키는 "상황.인물" (예: "calm.2.sio", "fb.chase.ir", "item.barley_tea.sua"). 줄 안의 {n} 같은 자리는 값으로 채운다.
// 시세가 갱신될 때마다(tick) 같은 상황에서 다른 줄을 고른다. 파일에 없는 키는 nil 이라 부르는 쪽이 원래 문장을 쓴다.
enum Lines {
    private struct File: Decodable { let lines: [String: [String]] }
    private static let all: [String: [String]] = {
        guard let url = Bundle.main.url(forResource: "lines_ko", withExtension: "json"),
              let d = try? Data(contentsOf: url), let f = try? JSONDecoder().decode(File.self, from: d) else { return [:] }
        return f.lines
    }()

    /// 데이터 갱신 번호: 시세를 받은 시각(분). 바뀔 때마다 대사가 바뀐다
    static var tick: Int { Int(Market.shared.updatedAt.timeIntervalSince1970 / 60) }

    /// key 의 줄 하나. salt 로 같은 화면의 다른 자리끼리 겹치지 않게 한다
    static func pick(_ key: String, _ vars: [String: String] = [:], salt: Int = 0) -> String? {
        guard let list = all[key], !list.isEmpty else { return nil }
        var h: UInt64 = 1469598103934665603
        for b in key.utf8 { h = (h ^ UInt64(b)) &* 1099511628211 }
        var x = h ^ UInt64(truncatingIfNeeded: tick &+ salt &* 7919)
        x = (x ^ (x >> 30)) &* 0xBF58476D1CE4E5B9
        x = (x ^ (x >> 27)) &* 0x94D049BB133111EB
        x ^= x >> 31
        var s = list[Int(x % UInt64(list.count))]
        for (k, v) in vars { s = s.replacingOccurrences(of: "{\(k)}", with: v) }
        return s
    }

    /// 전체 줄 수 (설정 등에서 보여 줄 때)
    static var count: Int { all.values.reduce(0) { $0 + $1.count } }
}
