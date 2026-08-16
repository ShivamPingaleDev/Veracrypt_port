import Foundation

enum FossConfig {
    /// Sideload / AltStore / source builds stay offline. App Store or GitHub
    /// builds can set VCPortEnableUpdateCheck=true in Info.plist.
    static var enableUpdateCheck: Bool {
        Bundle.main.object(forInfoDictionaryKey: "VCPortEnableUpdateCheck") as? Bool ?? false
    }
}
