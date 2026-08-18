import Foundation

enum FossConfig {
    /// Master builds stay offline. Live Check for updates is on experimental-biometrics.
    static var enableUpdateCheck: Bool {
        Bundle.main.object(forInfoDictionaryKey: "VCPortEnableUpdateCheck") as? Bool ?? false
    }
}
