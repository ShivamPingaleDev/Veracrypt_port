import Foundation

enum IdleUnit: String, CaseIterable, Identifiable {
    case minutes = "min"
    case hours = "h"

    var id: String { rawValue }
    var minutesPer: Int { self == .minutes ? 1 : 60 }
}

enum SessionIdle {
    static let maxMinutes = 24 * 60

    static func toMinutes(amount: Int, unit: IdleUnit) -> Int {
        if amount <= 0 { return 0 }
        let raw = amount * unit.minutesPer
        return min(max(raw, 0), maxMinutes)
    }

    static func split(_ minutes: Int) -> (amount: Int, unit: IdleUnit) {
        let n = min(max(minutes, 0), maxMinutes)
        if n >= 60, n % 60 == 0 {
            return (n / 60, .hours)
        }
        return (n, .minutes)
    }

    static func label(_ minutes: Int) -> String {
        let n = min(max(minutes, 0), maxMinutes)
        if n == 0 { return "Off" }
        if n == 1 { return "1 minute" }
        if n % 60 == 0 {
            let h = n / 60
            return h == 1 ? "1 hour" : "\(h) hours"
        }
        return "\(n) minutes"
    }
}
