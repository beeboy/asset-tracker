import Foundation
import Security
import CryptoKit
import CommonCrypto

/// 로그인 정보. 사이트의 '기기 자동 동기화'와 같은 방식
/// - password: 동기화 비밀번호에서 만든 저장 위치(id) + 암호 키. 비밀번호 자체는 저장하지 않는다
/// - github: 개발자 기기 동기화 (저장소 쓰기 권한이 있는 GitHub 토큰)
enum Login: Codable, Equatable {
    case password(id: String, key: Data)
    case github(token: String)
    case demo // 체험 모드: 받지도 풀지도 않고 예시 값만 보여 준다

    var label: String {
        switch self {
        case .demo: return "체험 모드"
        case .password: return "동기화 비밀번호"
        case .github: return "GitHub 토큰 (개발자)"
        }
    }
}

enum Credentials {
    private static let account = "naeilo-login"

    static func load() -> Login? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account,
                                kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return try? JSONDecoder().decode(Login.self, from: d)
    }

    static func save(_ l: Login) {
        clear()
        guard let d = try? JSONEncoder().encode(l) else { return }
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account, kSecValueData as String: d,
                                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        SecItemAdd(q as CFDictionary, nil)
    }

    static func clear() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account] as CFDictionary)
    }

    /// 사이트 esDerive 와 같음: PBKDF2-SHA256(비밀번호 NFC, "naeilo-sync-v1", 300000회) 512비트 →
    /// 앞 32바이트의 SHA-256 앞 20바이트 hex = id, 뒤 32바이트 = AES 키
    static func derive(password: String) -> Login? {
        let pw = Array(password.precomposedStringWithCanonicalMapping.utf8)
        let salt = Array("naeilo-sync-v1".utf8)
        var bits = [UInt8](repeating: 0, count: 64)
        let st = pw.withUnsafeBufferPointer { p in
            p.baseAddress!.withMemoryRebound(to: Int8.self, capacity: pw.count) { pp in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pp, pw.count, salt, salt.count,
                                     CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 300_000, &bits, bits.count)
            }
        }
        guard st == Int32(kCCSuccess) else { return nil }
        let id = SHA256.hash(data: Data(bits[0..<32])).prefix(20).map { String(format: "%02x", $0) }.joined()
        return .password(id: id, key: Data(bits[32..<64]))
    }

    /// 사이트 형식 "iv(base64).암호문+태그(base64)" 을 푼다
    static func decrypt(_ c: String, key: Data) throws -> Data {
        let parts = c.split(separator: ".").map(String.init)
        guard parts.count == 2, let iv = Data(base64Encoded: parts[0]), let body = Data(base64Encoded: parts[1]), body.count >= 16 else {
            throw SyncError.badCipher
        }
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: body.dropLast(16), tag: body.suffix(16))
        return try AES.GCM.open(box, using: SymmetricKey(data: key))
    }
}

enum SyncError: LocalizedError {
    case badCipher, wrongPassword, noData, http(Int), server(String)
    var errorDescription: String? {
        switch self {
        case .badCipher: return "동기화 값 형식이 맞지 않습니다"
        case .wrongPassword: return "풀 수 없습니다. 동기화 비밀번호를 확인하세요"
        case .noData: return "동기화된 입력값이 없습니다. 사이트에서 같은 비밀번호로 동기화를 켜 주세요"
        case .http(let c): return "서버 응답 \(c)"
        case .server(let m): return m
        }
    }
}
