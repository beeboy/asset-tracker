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

    @MainActor
    init(_ m: AppModel) {
        at = Date(); holdings = m.holdings; progress = m.progress; homeFriend = m.homeFriend
        custom = CustomSymbols.shared.items; hist = CustomSymbols.shared.hist
    }

    /// 불러오기: 추가한 종목 시세부터 넣고(종목 이름·가격이 있어야 보유가 보인다) 종목·진행을 덮는다
    @MainActor
    func restore(to m: AppModel) {
        CustomSymbols.shared.restore(custom, hist)
        m.apply(progress)
        if let h = homeFriend { m.homeFriend = h }
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
