import Foundation

/// Words for a person who already left. Panic does not use this.
enum MountLeave {
    static let kept = "Dismounted. Create form kept."

    static func status(wasOpen: Bool, failedLabels: [String], statusAlreadyPanic: Bool) -> String? {
        if !wasOpen || statusAlreadyPanic { return nil }
        guard let fail = MountReport.saveFailure(failedLabels) else { return kept }
        return "\(kept) \(fail)"
    }
}
