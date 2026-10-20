import Foundation
import Observation

// 인물 대사 데이터: Resources/lines_ko.json · lines_app_en.json (tools/lines.py 가 만든다, 146개 상황 × 인물별 최대 100줄). 앱 언어가 영어면 영어 파일만 읽는다.
// 키는 "상황.인물" (예: "calm.2.sio", "fb.chase.ir", "item.barley_tea.sua"). 줄 안의 {n} 같은 자리는 값으로 채운다.
// 볼 때마다(화면을 열거나 앱을 앞으로 가져오거나 당겨서 갱신) 같은 상황에서 다음 줄을 고른다. 파일에 없는 키는 nil 이라 부르는 쪽이 원래 문장을 쓴다.
enum Lines {
    private struct File: Decodable { let lines: [String: [String]] }
    private static let all: [String: [String]] = {
        // 앱 언어에 맞는 파일 하나만 읽는다 (영어: lines_app_en.json, 그 밖: lines_ko.json)
        AppModel.en ? load("lines_app_en") : load("lines_ko")
    }()
    private static func load(_ name: String) -> [String: [String]] {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json"),
              let d = try? Data(contentsOf: url), let f = try? JSONDecoder().decode(File.self, from: d) else { return [:] }
        return f.lines
    }

    /// key 의 줄 하나. 볼 때(차례가 바뀐 뒤 처음 그릴 때)만 다음 줄로 넘어가서, 한 바퀴 돌 때까지 겹치지 않는다.
    /// salt 로 같은 화면의 다른 자리끼리 따로 센다
    static func pick(_ key: String, _ vars: [String: String] = [:], salt: Int = 0) -> String? {
        guard let list = all[key], !list.isEmpty else { return nil }
        let id = salt == 0 ? key : "\(key)#\(salt)"
        let turn = LineClock.shared.turn
        var p = LineClock.pos[id] ?? LineClock.Pos(turn: turn, i: start(key, list.count))
        if p.turn != turn { p.turn = turn; p.i += 1; LineClock.pos[id] = p; LineClock.save() }
        else if LineClock.pos[id] == nil { LineClock.pos[id] = p; LineClock.save() }
        return fill(list[p.i % list.count], vars)
    }

    /// 지금 줄 다음부터 n줄 (넘기지 않는다). 위젯이 갱신할 때마다 하나씩 쓴다
    static func peek(_ key: String, _ vars: [String: String] = [:], _ n: Int) -> [String] {
        guard let list = all[key], !list.isEmpty else { return [] }
        let i = LineClock.pos[key]?.i ?? start(key, list.count) - 1
        return (1...min(n, list.count)).map { fill(list[(i + $0) % list.count], vars) }
    }

    /// 위젯에서 본 만큼 앞으로 넘긴다
    static func skip(_ key: String, _ n: Int) {
        guard n > 0, let list = all[key], !list.isEmpty else { return }
        var p = LineClock.pos[key] ?? LineClock.Pos(turn: LineClock.shared.turn, i: start(key, list.count) - 1)
        p.i += n; LineClock.pos[key] = p; LineClock.save()
    }

    /// 처음 줄은 키마다 다르게 (같은 화면의 인물들이 같은 번호부터 시작하지 않게)
    private static func start(_ key: String, _ n: Int) -> Int {
        var h: UInt64 = 1469598103934665603
        for b in key.utf8 { h = (h ^ UInt64(b)) &* 1099511628211 }
        return Int(h % UInt64(n))
    }
    private static func fill(_ line: String, _ vars: [String: String]) -> String {
        var s = line
        for (k, v) in vars { s = s.replacingOccurrences(of: "{\(k)}", with: v) }
        return s
    }

    /// 전체 줄 수 (설정 등에서 보여 줄 때)
    static var count: Int { all.values.reduce(0) { $0 + $1.count } }
}

/// 대사 차례: 화면을 열거나, 앱을 앞으로 가져오거나, 당겨서 갱신할 때 하나씩 오른다.
/// 1분 자동 갱신으로는 오르지 않아서, 보지 않은 대사를 건너뛰지 않는다
@Observable
final class LineClock {
    static let shared = LineClock()
    private(set) var turn = UserDefaults.standard.integer(forKey: "lineTurn")
    func next() { turn += 1; UserDefaults.standard.set(turn, forKey: "lineTurn") }

    struct Pos: Codable { var turn: Int; var i: Int }
    /// 상황·인물별로 마지막에 보여 준 줄
    static var pos: [String: Pos] = {
        guard let d = UserDefaults.standard.data(forKey: "linePos"), let p = try? JSONDecoder().decode([String: Pos].self, from: d) else { return [:] }
        return p
    }()
    static func save() { if let d = try? JSONEncoder().encode(pos) { UserDefaults.standard.set(d, forKey: "linePos") } }
}
