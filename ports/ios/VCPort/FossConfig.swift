import Foundation

enum FossConfig {
    /// Master builds stay offline. This branch stays offline too. experimental-biometrics is stale.
    static var enableUpdateCheck: Bool {
        Bundle.main.object(forInfoDictionaryKey: "VCPortEnableUpdateCheck") as? Bool ?? false
    }

    /// Off. This branch hardens USB + preview first; no Face ID extra.
    static var enableBiometrics: Bool {
        Bundle.main.object(forInfoDictionaryKey: "VCPortEnableBiometrics") as? Bool ?? false
    }

    /// In-app preview (not VLC / Files). Off on master until this module is merged.
    static var enableInAppPreview: Bool {
        Bundle.main.object(forInfoDictionaryKey: "VCPortEnableInAppPreview") as? Bool ?? false
    }

    /// Whole-disk USB Open is Android-only. iOS never enables it.
    static var enableOtgDisk: Bool { false }
}
