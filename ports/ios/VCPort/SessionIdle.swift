import Foundation

enum SessionIdle {
    static let minutes = [0, 1, 5, 15]

    static func label(_ minutes: Int) -> String {
        switch minutes {
        case 0: return "Off"
        case 1: return "1 minute"
        case 5: return "5 minutes"
        case 15: return "15 minutes"
        default: return "\(minutes) minutes"
        }
    }
}
