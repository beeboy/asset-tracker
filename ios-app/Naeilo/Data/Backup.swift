import SwiftUI
import UniformTypeIdentifiers

// 내 폰에 저장하기·불러오기: 동기화 없이 종목·미션 진행·추가한 종목 시세를 파일 하나로.
// 파일 앱의 '나의 iPhone'이나 iCloud Drive 에 두고, 새 폰이나 앱을 지웠다 깐 뒤 불러온다.
// 파일은 암호화하지 않는다 (보유 내역이 그대로 들어 있으니 남에게 보내지 않게 안내한다).
struct Backup: Codable {
    var v = 1
    var at: Date
    var holdings: [Holding]
    var progress: Progress
    var homeFriend: String?
    var custom: [CustomSymbols.Saved]
    var hist: [String: CustomSymbols.Hist]
    var daily: [DayRec]? = nil     // 내 기록 (예전 파일에는 없다)
    var town: [String: Data]? = nil  // 동네: 주민 수첩(이름·모자·이사 온 날)·대화 기록·별사탕 (예전 파일에는 없다)
    var krLog: [MC.KrDay]? = nil   // 국내 주식 합계 날짜별 금액. 계획선 출발값에 쓴다 (예전 파일에는 없다)
    var site: Data? = nil   // 계산에 쓰는 사이트 칸(사건·전망 설정·현금). 동기화를 켜든 끄든 같은 숫자가 나오게 (예전 파일에는 없다)
    var calm: Calm? = nil   // 투자 습관 기록 (예전 파일에는 없다)
    var pworld: PWorld? = nil   // 평행 세계·모의바구니 (예전 파일에는 없다)
    var lots: [Lot]? = nil   // 수량 기록 (날짜별 보유 수량). 내 길 실제 평가액·계획선 출발값에 쓴다 (예전 파일에는 없다)
    var calc: CalcSnap? = nil   // 계산 자료(전망 입력값). 불러온 기기도 같은 입력으로 센다 (예전 파일에는 없다)
    static let townKeys = ["town.book", "town.log"]

    @MainActor
    init(_ m: AppModel) {
        at = Date(); holdings = m.holdings; progress = m.progress; homeFriend = m.homeFriend
        custom = CustomSymbols.shared.items; hist = CustomSymbols.shared.hist; daily = m.myRecord; krLog = CustomSymbols.shared.krLog; site = Sync.shared.calcSite
        calm = m.calm; pworld = m.pworld; lots = m.lots; calc = Market.shared.calcOut
        var t: [String: Data] = [:]
        for k in Self.townKeys { if let d = UserDefaults.standard.data(forKey: k) { t[k] = d } }
        // 정수 값: 별사탕 + 파도 타기(꾸미기·섬 별·최고 점수)
        for k in ["town.candy"] + SurfCloset.keys { t[k] = Data(String(UserDefaults.standard.integer(forKey: k)).utf8) }
        town = t
    }

    /// 불러오기: 추가한 종목 시세부터 넣고(종목 이름·가격이 있어야 보유가 보인다) 종목·진행을 덮는다
    @MainActor
    func restore(to m: AppModel) {
        CustomSymbols.shared.restore(custom, hist, kr: krLog)
        if let site { Sync.shared.restoreCalcSite(site) }
        if let calm { m.calm = calm }
        if let pworld { m.pworld = pworld }
        if let lots { m.lots = lots }
        if let calc { Market.shared.takeCalc(calc) }
        m.apply(progress)
        if let h = homeFriend { m.homeFriend = h }
        if let daily { m.myRecord = MyRecord.merge(m.myRecord, daily) }
        if let town {
            for k in Self.townKeys { if let d = town[k] { UserDefaults.standard.set(d, forKey: k) } }
            for k in ["town.candy"] + SurfCloset.keys {
                if let c = town[k].flatMap({ Int(String(decoding: $0, as: UTF8.self)) }) { UserDefaults.standard.set(c, forKey: k) }
            }
            SurfCloset.keep()
            TownBook.shared.reload(); TownLog.shared.reload()
        }
        m.holdings = holdings
        m.saveProgress()
    }

    static func fileName(_ d: Date = Date()) -> String {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"
        return "naeilo-\(f.string(from: d))"
    }
}

/// 파일 내보내기용 문서 (JSON)
struct BackupFile: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(_ b: Backup) {
        let e = JSONEncoder(); e.dateEncodingStrategy = .iso8601; e.outputFormatting = [.sortedKeys]
        data = (try? e.encode(b)) ?? Data()
    }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }

    static func read(_ url: URL) throws -> Backup {
        let ok = url.startAccessingSecurityScopedResource()
        defer { if ok { url.stopAccessingSecurityScopedResource() } }
        let d = JSONDecoder(); d.dateDecodingStrategy = .iso8601
        return try d.decode(Backup.self, from: Data(contentsOf: url))
    }
}
