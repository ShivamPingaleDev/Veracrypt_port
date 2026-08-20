import Foundation
import Security

struct FactorBundle {
    var pim: Int = 0
    var password: String = ""
    var biometricKey: Data?
    var keyfilePaths: [String] = []

    var hasBiometric: Bool { biometricKey.map { !$0.isEmpty } ?? false }
}

enum FactorCodec {
    static func encode(_ bundle: FactorBundle) -> Data {
        let pw = Data(bundle.password.utf8).base64EncodedString()
        let bio = bundle.biometricKey?.base64EncodedString() ?? ""
        var text = "VCF2\n\(bundle.pim)\n\(pw)\n\(bio)\n"
        for path in bundle.keyfilePaths {
            text += path + "\n"
        }
        return Data(text.utf8)
    }

    static func decode(_ data: Data) -> FactorBundle {
        guard let text = String(data: data, encoding: .utf8) else { return FactorBundle() }
        if !text.hasPrefix("VCF2\n") {
            let parts = text.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
            let pim = parts.first.flatMap { Int($0) } ?? 0
            let password = parts.count > 1 ? String(parts[1]) : ""
            return FactorBundle(pim: pim, password: password)
        }
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let pim = Int(lines.count > 1 ? lines[1] : "0") ?? 0
        let password: String = {
            guard lines.count > 2, !lines[2].isEmpty, let data = Data(base64Encoded: lines[2]) else { return "" }
            return String(data: data, encoding: .utf8) ?? ""
        }()
        let bio: Data? = {
            guard lines.count > 3, !lines[3].isEmpty else { return nil }
            return Data(base64Encoded: lines[3])
        }()
        let paths = lines.count > 4 ? Array(lines.dropFirst(4)).filter { !$0.isEmpty } : []
        return FactorBundle(pim: pim, password: password, biometricKey: bio, keyfilePaths: paths)
    }

    static func randomBiometricKey() -> Data {
        var bytes = [UInt8](repeating: 0, count: 64)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes)
    }
}
