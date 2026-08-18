import Foundation
import SwiftUI

extension Notification.Name {
    static let vcPortTestingColdStart = Notification.Name("dev.shivampingale.vcport.testingColdStart")
}

extension View {
    func portTag(_ id: String) -> some View {
        accessibilityIdentifier(id)
    }
}

/// In-process session tests skip Files / share sheets the same way Android
/// instrumented tests skip SAF. Production still uses the system pickers.
final class VcPortTesting {
    static let shared = VcPortTesting()

    var skipSystemPickers = false
    var ready = false

    var status: () -> String = { "" }
    var createPassword: () -> String = { "" }
    var volumePassword: () -> String = { "" }
    var createPim: () -> String = { "" }
    var volumePim: () -> String = { "" }
    var hiddenCreatePassword: () -> String = { "" }
    var basketEmpty: () -> Bool = { true }
    var entryNames: () -> [String] = { [] }
    var volumeInfo: () -> String? = { nil }
    var keyfileURLs: () -> [URL] = { [] }
    var containerName: () -> String = { "" }

    var selectTab: (Int) -> Void = { _ in }
    var setCreateCipher: (String) -> Void = { _ in }
    var setCreateKdf: (String) -> Void = { _ in }
    var setCreatePim: (String) -> Void = { _ in }
    var setCreateFilename: (String) -> Void = { _ in }
    var setCreateSize: (String) -> Void = { _ in }
    var setCreateHidden: (Bool) -> Void = { _ in }
    var setCreateHiddenPim: (String) -> Void = { _ in }
    var setCreateHiddenSize: (String) -> Void = { _ in }
    var setVolumePassword: (String) -> Void = { _ in }
    var setVolumePim: (String) -> Void = { _ in }
    var setProtectHidden: (Bool) -> Void = { _ in }
    var setHiddenProtectPassword: (String) -> Void = { _ in }
    var setHiddenProtectPim: (String) -> Void = { _ in }
    var setUseBackupHeader: (Bool) -> Void = { _ in }
    var setReadOnly: (Bool) -> Void = { _ in }
    var setNewPassword: (String) -> Void = { _ in }
    var setNewPim: (String) -> Void = { _ in }
    var setHeaderKdf: (String) -> Void = { _ in }
    var setKeyfileGenName: (String) -> Void = { _ in }

    var fillEntropy: () -> Void = {}
    var generateCreatePassword: () -> Void = {}
    var copyOnce: () -> Void = {}
    var generateNestedPassword: () -> Void = {}
    var copyNestedOnce: () -> Void = {}
    var generateKeyfile: () -> Void = {}
    var generateToolsKeyfile: () -> Void = {}
    var createVolume: () -> Void = {}
    var openVolume: () -> Void = {}
    var lockSession: () -> Void = {}
    var showVolumeProperties: () -> Void = {}
    var backupHeader: () -> Void = {}
    var changePassword: () -> Void = {}
    var setKdf: () -> Void = {}
    var applyKeyfiles: () -> Void = {}
    var removeAllKeyfiles: () -> Void = {}
    var restoreEmbedded: () -> Void = {}
    var wipeFreeSpace: () -> Void = {}
    var mkdir: (String) -> Void = { _ in }
    var addBasketFiles: ([URL]) -> Void = { _ in }
    var finishCreateSave: (URL) -> Bool = { _ in false }
    var selectContainer: (URL) -> Void = { _ in }
    var clearKeyfiles: () -> Void = {}
    var addKeyfiles: ([URL]) -> Void = { _ in }
    var importFiles: ([URL]) -> Void = { _ in }
    var exportNamed: (String, URL) -> Bool = { _, _ in false }
    var openDir: (String) -> Void = { _ in }
    var goParent: () -> Void = {}
    var transferNamed: (Set<String>, String, Bool) -> Bool = { _, _, _ in false }
    var restoreHeader: (URL) -> Void = { _ in }
    var copyHeaderBackup: (URL) -> Bool = { _ in false }
    var homeLeave: () -> Void = {}
    var selectMountSlot: (Int) -> Void = { _ in }
    var coldStart: () -> Void = {
        NotificationCenter.default.post(name: .vcPortTestingColdStart, object: nil)
    }
}
