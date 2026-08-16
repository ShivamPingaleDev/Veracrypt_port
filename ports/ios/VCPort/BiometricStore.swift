import Foundation
import LocalAuthentication
import Security

enum BiometricStore {
    static let service = "dev.shivampingale.vcport.volume-password"

    static var isAvailable: Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    static func hasPassword(for path: String) -> Bool {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path,
            kSecReturnData as String: false
        ]
        return SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess ||
            SecItemCopyMatching(query as CFDictionary, nil) == errSecInteractionNotAllowed
    }

    static func store(path: String, password: String, pim: Int) -> Bool {
        delete(path: path)
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .biometryCurrentSet,
            &error
        ) else { return false }
        let payload = "\(pim)\n\(password)".data(using: .utf8) ?? Data()
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path,
            kSecValueData as String: payload,
            kSecAttrAccessControl as String: access
        ]
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }

    static func load(path: String) -> (String, Int)? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseOperationPrompt as String: "Unlock the volume with Face ID or Touch ID"
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data,
              let text = String(data: data, encoding: .utf8) else { return nil }
        let parts = text.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2, let pim = Int(parts[0]) else { return nil }
        return (String(parts[1]), pim)
    }

    static func delete(path: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path
        ]
        SecItemDelete(query as CFDictionary)
    }
}
