import Foundation

enum PasswordEntropy {
    static let generatorAlphabet =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+"

    static func bits(_ password: String) -> Int {
        if password.isEmpty { return 0 }
        let pool = Double(max(poolSize(password), 1))
        return Int(Double(password.count) * log2(pool))
    }

    static func label(_ password: String) -> String {
        let b = bits(password)
        if password.isEmpty {
            return "Entropy: none."
        }
        if password.count < 16 {
            return "Entropy: about \(b) bits — too short."
        }
        if b >= 256 {
            return "Entropy: about \(b) bits."
        }
        if b >= 80 {
            return "Entropy: about \(b) bits."
        }
        return "Entropy: about \(b) bits — weak."
    }

    private static func poolSize(_ password: String) -> Int {
        if password.allSatisfy({ generatorAlphabet.contains($0) }) {
            return generatorAlphabet.count
        }
        var pool = 0
        if password.contains(where: { $0 >= "A" && $0 <= "Z" }) { pool += 26 }
        if password.contains(where: { $0 >= "a" && $0 <= "z" }) { pool += 26 }
        if password.contains(where: { $0.isNumber }) { pool += 10 }
        if password.contains(where: { !$0.isLetter && !$0.isNumber }) { pool += 32 }
        return max(pool, Set(password).count)
    }
}

enum SizeUnit: String, CaseIterable, Identifiable {
    case kib = "KiB"
    case mib = "MiB"
    case gib = "GiB"
    var id: String { rawValue }
    var factor: UInt64 {
        switch self {
        case .kib: return 1024
        case .mib: return 1024 * 1024
        case .gib: return 1024 * 1024 * 1024
        }
    }

    static let minVolume: UInt64 = 2 * 1024 * 1024
    static let maxVolume: UInt64 = 64 * 1024 * 1024 * 1024

    static func toBytes(amount: UInt64, unit: SizeUnit) -> UInt64 {
        let f = unit.factor
        if amount > UInt64.max / f { return UInt64.max }
        return amount * f
    }

    static func formatBytes(_ bytes: UInt64) -> String {
        if bytes >= 1024 * 1024 * 1024 {
            let g = Double(bytes) / Double(1024 * 1024 * 1024)
            return String(format: "%.2f GiB", g)
        }
        if bytes >= 1024 * 1024 {
            let m = (bytes + (1 << 20) - 1) / (1 << 20)
            return "\(m) MiB"
        }
        let k = (bytes + 1023) / 1024
        return "\(k) KiB"
    }

    /// Whole number + unit for the Create size field. Rounds up to fit.
    static func fit(_ bytes: UInt64) -> (UInt64, SizeUnit) {
        let n = min(max(bytes, minVolume), maxVolume)
        if n >= gib.factor && n % gib.factor == 0 {
            return (n / gib.factor, .gib)
        }
        if n >= mib.factor {
            let m = (n + mib.factor - 1) / mib.factor
            if m >= 1024 && m % 1024 == 0 {
                return (m / 1024, .gib)
            }
            return (m, .mib)
        }
        return ((n + 1023) / 1024, .kib)
    }
}
