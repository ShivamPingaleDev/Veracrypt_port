import Foundation

/// Master builds stay offline. Live Check for updates is on experimental-biometrics.
enum UpdateChecker {
    static var localVersion: String { SourcePin.localVersion }

    static func check() -> Never {
        fatalError("This build has no network")
    }
}
