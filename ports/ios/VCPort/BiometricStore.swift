import Foundation
import LocalAuthentication
import Security

enum BiometricStore {
    static let service = "dev.shivampingale.vcport.volume-password"

    static var isAvailable: Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
    }

    static func hasFactors(for path: String) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path,
            kSecReturnData as String: false
        ]
        return SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess ||
            SecItemCopyMatching(query as CFDictionary, nil) == errSecInteractionNotAllowed
    }

    static func store(path: String, bundle: FactorBundle) -> Bool {
        delete(path: path)
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .userPresence,
            &error
        ) else { return false }
        let payload = FactorCodec.encode(bundle)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path,
            kSecValueData as String: payload,
            kSecAttrAccessControl as String: access
        ]
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }

    static func load(path: String) -> FactorBundle? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
            kSecUseOperationPrompt as String: "Unlock with Face ID, Touch ID, or passcode"
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return FactorCodec.decode(data)
    }

    static func delete(path: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: path
        ]
        SecItemDelete(query as CFDictionary)
    }

    static func deleteAll() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service
        ]
        SecItemDelete(query as CFDictionary)
    }
}
