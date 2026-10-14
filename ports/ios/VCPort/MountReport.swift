import Foundation

/// The only sentences the person sees for a save. The checks stay at the call site.
enum MountReport {
    static func saveFailure(_ failedLabels: [String]) -> String? {
        if failedLabels.isEmpty { return nil }
        let which = failedLabels.count == 1 ? failedLabels[0] : String(failedLabels.count)
        return "Not saved: \(which). Original unchanged."
    }

    static func dismount(label: String, remaining: Int, hadSource: Bool, saved: Bool) -> String {
        if hadSource && !saved { return "Dismounted \(label). Not saved." }
        if hadSource && saved {
            if remaining == 0 { return "Saved. Dismounted \(label)." }
            return "Saved. Dismounted \(label). \(remaining) still open."
        }
        if remaining == 0 { return "Dismounted \(label)." }
        return "Dismounted \(label). \(remaining) still open."
    }

    static func lockStatus(_ finishStatus: String, failedLabels: [String]) -> String {
        guard let fail = saveFailure(failedLabels) else { return finishStatus }
        return "\(finishStatus) \(fail)"
    }
}
