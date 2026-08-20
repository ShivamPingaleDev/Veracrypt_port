import Foundation

/// Master builds stay offline. This branch stays offline too. experimental-biometrics is stale.
enum UpdateChecker {
    static var localVersion: String { SourcePin.localVersion }

    static func check() -> Never {
        fatalError("This build has no network")
    }
}
