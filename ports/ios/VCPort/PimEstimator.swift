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
        let formatted = formatCount(n)
        let pimBit = pim <= 0 ? "PIM 0 (VeraCrypt default)" : "PIM \(pim)"
        return "\(kdf): about \(formatted) header iterations (\(pimBit)). Not a crack-time estimate. Benchmark measures cipher speed, not this."
    }

    /// Fixed grouping so 500000 stays "500,000" on every phone locale.
    private static func formatCount(_ n: Int) -> String {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US")
        formatter.numberStyle = .decimal
        return formatter.string(from: NSNumber(value: n)) ?? String(n)
    }
}
