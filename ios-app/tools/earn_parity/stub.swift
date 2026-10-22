import Foundation

// 검사용 대역: 앱의 Config·Store (WidgetShared/Config.swift 는 다른 파일에 기대어 따로 컴파일되지 않는다)
enum Config { static let data = URL(string: "https://naeilo.com/data/")! }
enum Store {
    static func readData(_ name: String) -> Data? { nil }
    static func writeData(_ d: Data, _ name: String) {}
}
