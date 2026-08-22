import Foundation

/// Honest PIM helper. Not Benchmark. Not a crack-time estimate.
enum PimEstimator {
    static func hmacIterations(_ pim: Int) -> Int {
        pim <= 0 ? 500_000 : pim * 1_000
    }

    static func describe(kdf: String, pimText: String) -> String {
        let pim = Int(pimText.filter(\.isNumber)) ?? 0
        if kdf.localizedCaseInsensitiveContains("Argon2") {
            return "Argon2id. PIM changes Argon2 time cost. This is not seconds-to-open and not a crack-time estimate."
        }
        let n = hmacIterations(pim)
        let formatted = NumberFormatter.localizedString(from: NSNumber(value: n), number: .decimal)
        let pimBit = pim <= 0 ? "PIM 0 (VeraCrypt default)" : "PIM \(pim)"
        return "\(kdf): about \(formatted) header iterations (\(pimBit)). Not a crack-time estimate. Benchmark measures cipher speed, not this."
    }
}
