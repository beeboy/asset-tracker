// swift-tools-version: 5.9
// 결 (다이내믹 아일랜드·잠금 화면). 앱과 떼어 둔 부분: 빼려면 Gyeol/README.md
import PackageDescription

let package = Package(
    name: "GyeolKit",
    defaultLocalization: "ko",
    platforms: [.iOS(.v17)],
    products: [.library(name: "GyeolKit", targets: ["GyeolKit"])],
    targets: [.target(name: "GyeolKit", resources: [.process("Resources")])]
)
