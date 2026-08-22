import CryptoKit
import UIKit
import XCTest
@testable import VCPort

/// Person session on the iPad Simulator through the app UI (10 phases): basket + nested
/// volume, nested folders, save wipes secrets, fill files, leave and reopen,
/// decrypt, mount several, Copy to volume / Move to volume, hidden volume
/// files, header backup/restore, KDF change, add/remove password and
/// keyfiles, then idle / in-volume hash / PIM estimate. Does not tap Panic wipe. Does not start Check for updates.
final class AppInterfaceSessionTests: XCTestCase {
    func testCreateSaveWipeReopenMountTransferAndSecurity() {
        continueAfterFailure = false
        waitReady()
        let t = VcPortTesting.shared
        t.skipSystemPickers = true
        VcMobileBridge.resetEntropy()

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let work = docs.appendingPathComponent("ui-session", isDirectory: true)
        try? FileManager.default.removeItem(at: work)
        try? FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        let basketDir = work.appendingPathComponent("basket", isDirectory: true)
        try? FileManager.default.createDirectory(at: basketDir, withIntermediateDirectories: true)

        let photoBytes = Data((0..<(64 * 1024)).map { UInt8(truncatingIfNeeded: $0) })
        let memoText = "basket-memo-ok\n"
        let loraBytes = Data(repeating: 0xA5, count: 4096)
        let zipBytes = Data([0x50, 0x4B, 0x03, 0x04, 0x14, 0x00])
        let photo = write(basketDir.appendingPathComponent("PHOTO.JPG"), photoBytes)
        let memo = write(basketDir.appendingPathComponent("MEMO.TXT"), Data(memoText.utf8))
        let lora = write(basketDir.appendingPathComponent("ADAPTER.BIN"), loraBytes)
        let zip = write(basketDir.appendingPathComponent("NOTES.ZIP"), zipBytes)
        let extraFill = write(work.appendingPathComponent("FILL.TXT"), Data("copied-in-after-open\n".utf8))
        let extraFill2 = write(work.appendingPathComponent("MORE.BIN"), Data(repeating: 7, count: 512))
        let nestedNote = write(work.appendingPathComponent("NEST.TXT"), Data("nested-folder-ok\n".utf8))
        let hiddenNote = write(work.appendingPathComponent("HIDDEN.TXT"), Data("hidden-volume-ok\n".utf8))
        let changedPassword = "basket-password-changed-ok"

        onMain { t.selectTab(1) }
        onMain { t.fillEntropy() }
        XCTAssertGreaterThanOrEqual(VcMobileBridge.entropyPercent(), 100)

        onMain { t.addBasketFiles([photo, memo, lora, zip]) }
        waitStatus("Volume will be at least", 12)
        let basketStatus = onMainValue { t.status() }
        XCTAssertTrue(basketStatus.contains("PHOTO.JPG") || onMainValue { t.status() }.contains("4 files"))

        onMain { t.setCreateCipher("AES(Twofish(Serpent))") }
        onMain { t.setCreateKdf("HMAC-SHA-512") }
        onMain { t.setCreatePim("1") }
        onMain { t.setCreateFilename("basket.jpg") }
        onMain { t.generateCreatePassword() }
        onMain { t.copyOnce() }
        let basketPassword = UIPasteboard.general.string
        XCTAssertNotNil(basketPassword)
        XCTAssertEqual(basketPassword?.count, 64)
        try? basketPassword?.write(to: work.appendingPathComponent("basket-password.txt"), atomically: true, encoding: .utf8)

        onMain { t.generateKeyfile() }
        let keyfiles = snapshotKeyfiles(into: work.appendingPathComponent("keys", isDirectory: true))
        XCTAssertFalse(keyfiles.isEmpty, "Generate keyfile and add must produce a file")

        onMain { t.createVolume() }
        waitStatus("from the basket into the volume", 180)
        let basketDest = work.appendingPathComponent("basket.jpg")
        XCTAssertTrue(onMainValue { t.finishCreateSave(basketDest) })
        waitStatus("Create secrets were wiped", 15)
        XCTAssertEqual(onMainValue { t.volumePassword() }, "")
        XCTAssertEqual(onMainValue { t.volumePim() }, "0")
        onMain { t.selectTab(1) }
        pump(0.3)
        XCTAssertEqual(onMainValue { t.createPassword() }, "")
        XCTAssertEqual(onMainValue { t.createPim() }, "0")
        XCTAssertTrue(onMainValue { t.basketEmpty() })

        onMain { t.fillEntropy() }
        onMain { t.generateCreatePassword() }
        onMain { t.copyOnce() }
        let outerPassword = UIPasteboard.general.string
        XCTAssertNotNil(outerPassword)
        XCTAssertEqual(outerPassword?.count, 64)
        XCTAssertNotEqual(outerPassword, basketPassword)

        onMain { t.setCreateHidden(true) }
        pump(0.2)
        onMain { t.generateNestedPassword() }
        onMain { t.copyNestedOnce() }
        let nestedPassword = UIPasteboard.general.string
        XCTAssertNotNil(nestedPassword)
        XCTAssertEqual(nestedPassword?.count, 64)
        XCTAssertNotEqual(nestedPassword, outerPassword)
        try? "\(outerPassword!)\n\(nestedPassword!)"
            .write(to: work.appendingPathComponent("nested-passwords.txt"), atomically: true, encoding: .utf8)

        onMain { t.setCreateCipher("AES(Twofish(Serpent))") }
        onMain { t.setCreateKdf("HMAC-SHA-512") }
        onMain { t.setCreatePim("1") }
        onMain { t.setCreateHiddenPim("1") }
        onMain { t.setCreateHiddenSize("2") }
        onMain { t.setCreateSize("8") }
        onMain { t.setCreateFilename("photos.jpg") }
        onMain { t.createVolume() }
        waitStatus("Nested volume is inside", 240)
        let nestedDest = work.appendingPathComponent("photos.jpg")
        XCTAssertTrue(onMainValue { t.finishCreateSave(nestedDest) })
        waitStatus("Create secrets were wiped", 15)
        XCTAssertEqual(onMainValue { t.volumePassword() }, "")
        onMain { t.selectTab(1) }
        pump(0.3)
        XCTAssertEqual(onMainValue { t.createPassword() }, "")
        XCTAssertEqual(onMainValue { t.hiddenCreatePassword() }, "")

        onMain { t.homeLeave() }
        pump(2.0)
        onMain { t.coldStart() }
        pump(1.0)
        t.skipSystemPickers = true
        waitReady()
        XCTAssertFalse(onMainValue { t.status() }.localizedCaseInsensitiveContains("Check for updates"))

        onMain { t.selectTab(0) }
        onMain { t.selectContainer(basketDest) }
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.addKeyfiles(keyfiles) }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)
        XCTAssertEqual(onMainValue { t.volumePassword() }, "")
        XCTAssertEqual(onMainValue { t.volumePim() }, "0")

        onMain { t.selectTab(3) }
        onMain { t.showVolumeProperties() }
        waitUntil(15) {
            let info = t.volumeInfo() ?? t.status()
            return info.contains("AES(Twofish(Serpent))") && info.contains("HMAC-SHA-512")
        }

        onMain { t.selectTab(2) }
        let names = onMainValue { t.entryNames() }
        XCTAssertTrue(names.contains("BASKET.sha256"))
        XCTAssertTrue(names.contains { $0.localizedCaseInsensitiveContains("MEMO") })
        XCTAssertTrue(names.contains { $0.localizedCaseInsensitiveContains("PHOTO") })

        let memoOut = work.appendingPathComponent("out-memo.txt")
        let memoName = names.first { $0.localizedCaseInsensitiveContains("MEMO") }!
        XCTAssertTrue(onMainValue { t.exportNamed(memoName, memoOut) })
        XCTAssertEqual(try String(contentsOf: memoOut, encoding: .utf8), memoText)
        let photoName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("PHOTO") }!
        let photoOut = work.appendingPathComponent("out-photo.jpg")
        XCTAssertTrue(onMainValue { t.exportNamed(photoName, photoOut) })
        XCTAssertEqual(sha256(photoBytes), sha256(try Data(contentsOf: photoOut)))

        onMain { t.importFiles([extraFill, extraFill2]) }
        waitUntil(30) {
            let listed = t.entryNames()
            return listed.contains { $0.localizedCaseInsensitiveContains("FILL") }
                && listed.contains { $0.localizedCaseInsensitiveContains("MORE") }
        }

        onMain { t.mkdir("INBOX") }
        waitStatus("Created folder INBOX", 30)
        onMain { t.openDir("INBOX") }
        waitUntil(15) {
            !t.entryNames().contains { $0.localizedCaseInsensitiveContains("FILL") }
        }
        onMain { t.importFiles([nestedNote]) }
        waitUntil(30) {
            t.entryNames().contains { $0.localizedCaseInsensitiveContains("NEST") }
        }
        let nestOut = work.appendingPathComponent("from-inbox-nest.txt")
        let nestName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("NEST") }!
        XCTAssertTrue(onMainValue { t.exportNamed(nestName, nestOut) })
        XCTAssertEqual(try String(contentsOf: nestOut, encoding: .utf8), "nested-folder-ok\n")
        onMain { t.goParent() }
        waitUntil(15) {
            t.entryNames().contains { $0.localizedCaseInsensitiveContains("INBOX") }
        }

        onMain { t.selectTab(2) }
        onMain { t.openMountedSlot() }
        onMain { t.selectMountSlot(0) }

        onMain { t.selectTab(0) }
        onMain { t.clearKeyfiles() }
        onMain { t.selectContainer(nestedDest) }
        onMain { t.setVolumePassword(outerPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.setProtectHidden(true) }
        onMain { t.setHiddenProtectPassword(nestedPassword!) }
        onMain { t.setHiddenProtectPim("1") }
        onMain { t.openVolume() }
        waitStatus("volumes mounted", 180)

        onMain { t.selectTab(2) }
        waitUntil(15) { t.status().contains("2 volumes mounted") }
        onMain { t.selectMountSlot(0) }
        pump(0.4)
        let fillName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("FILL") }
        XCTAssertNotNil(fillName, "FILL was imported into the basket volume")
        XCTAssertTrue(
            onMainValue { t.transferNamed([fillName!], nestedDest.lastPathComponent, false) },
            "copy FILL into photos.jpg: \(onMainValue { t.status() })"
        )
        waitStatus("Copied 1 file(s) into", 60)

        let moreName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("MORE") }!
        XCTAssertTrue(
            onMainValue { t.transferNamed([moreName], nestedDest.lastPathComponent, true) },
            "move MORE into photos.jpg: \(onMainValue { t.status() })"
        )
        waitStatus("Moved 1 file(s) into", 60)
        XCTAssertFalse(onMainValue { t.entryNames() }.contains { $0.caseInsensitiveCompare(moreName) == .orderedSame })

        onMain { t.selectTab(2) }
        onMain { t.selectMountSlot(1) }
        pump(0.4)
        waitUntil(15) {
            let listed = t.entryNames()
            return listed.contains { $0.localizedCaseInsensitiveContains("FILL") }
                && listed.contains { $0.localizedCaseInsensitiveContains("MORE") }
        }

        let fillOut = work.appendingPathComponent("from-nested-fill.txt")
        let fillOnPhotos = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("FILL") }!
        XCTAssertTrue(onMainValue { t.exportNamed(fillOnPhotos, fillOut) })
        XCTAssertEqual(try String(contentsOf: fillOut, encoding: .utf8), "copied-in-after-open\n")
        let moreOut = work.appendingPathComponent("from-nested-more.bin")
        let moreOnPhotos = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("MORE") }!
        XCTAssertTrue(onMainValue { t.exportNamed(moreOnPhotos, moreOut) })
        XCTAssertEqual((try Data(contentsOf: moreOut)).count, 512)

        XCTAssertTrue(
            onMainValue { t.transferNamed([moreOnPhotos], basketDest.lastPathComponent, false) },
            "copy MORE back into basket.jpg: \(onMainValue { t.status() })"
        )
        waitStatus("Copied 1 file(s) into", 60)

        onMain { t.selectTab(3) }
        onMain { t.lockSession() }
        waitStatus("Dismounted", 30)

        onMain { t.clearKeyfiles() }
        onMain { t.selectContainer(nestedDest) }
        onMain { t.selectTab(0) }
        onMain { t.setVolumePassword(nestedPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)

        onMain { t.selectTab(2) }
        onMain { t.mkdir("SECRET") }
        waitStatus("Created folder SECRET", 30)
        onMain { t.openDir("SECRET") }
        waitUntil(15) {
            !t.entryNames().contains { $0.localizedCaseInsensitiveContains("SECRET") }
        }
        onMain { t.importFiles([hiddenNote]) }
        waitUntil(30) {
            t.entryNames().contains { $0.localizedCaseInsensitiveContains("HIDDEN") }
        }
        let hiddenOut = work.appendingPathComponent("from-hidden.txt")
        let hiddenName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("HIDDEN") }!
        XCTAssertTrue(onMainValue { t.exportNamed(hiddenName, hiddenOut) })
        XCTAssertEqual(try String(contentsOf: hiddenOut, encoding: .utf8), "hidden-volume-ok\n")

        onMain { t.selectTab(3) }
        onMain { t.lockSession() }
        waitStatus("Dismounted", 30)

        onMain { t.selectContainer(basketDest) }
        onMain { t.selectTab(0) }
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.addKeyfiles(keyfiles) }
        onMain { t.selectTab(3) }
        onMain { t.backupHeader() }
        waitStatus("Header backup ready", 60)
        let headerBak = work.appendingPathComponent("basket-header.bak")
        XCTAssertTrue(onMainValue { t.copyHeaderBackup(headerBak) })

        onMain { t.setNewPassword(changedPassword) }
        onMain { t.setNewPim("1") }
        onMain { t.changePassword() }
        waitStatus("Changed volume password", 60)

        onMain { t.selectTab(0) }
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.openVolume() }
        waitStatus("Wrong password", 60)
        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)
        let memoAfterChange = work.appendingPathComponent("memo-after-password.txt")
        let memoAfterName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("MEMO") }!
        XCTAssertTrue(onMainValue { t.exportNamed(memoAfterName, memoAfterChange) })
        XCTAssertEqual(try String(contentsOf: memoAfterChange, encoding: .utf8), memoText)
        XCTAssertTrue(onMainValue { t.entryNames() }.contains { $0.localizedCaseInsensitiveContains("INBOX") })

        onMain { t.selectTab(3) }
        onMain { t.lockSession() }
        waitStatus("Dismounted", 30)

        onMain { t.selectTab(0) }
        onMain { t.selectContainer(basketDest) }
        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.setVolumePim("1") }
        onMain { t.addKeyfiles(keyfiles) }
        onMain { t.selectTab(3) }
        onMain { t.setHeaderKdf("HMAC-SHA-256") }
        onMain { t.setKdf() }
        waitStatus("Set header key derivation algorithm to HMAC-SHA-256", 60)

        onMain { t.selectTab(0) }
        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)
        onMain { t.selectTab(3) }
        onMain { t.showVolumeProperties() }
        waitUntil(15) {
            let info = t.volumeInfo() ?? t.status()
            return info.contains("HMAC-SHA-256")
        }

        onMain { t.setKeyfileGenName("extra.bin") }
        onMain { t.generateToolsKeyfile() }
        waitStatus("Generated and added", 15)
        let extraKeys = snapshotKeyfiles(into: work.appendingPathComponent("keys-plus", isDirectory: true))
        XCTAssertGreaterThanOrEqual(extraKeys.count, 2)
        onMain { t.applyKeyfiles() }
        waitStatus("Applied the current keyfile list", 60)

        onMain { t.selectTab(0) }
        onMain { t.clearKeyfiles() }
        onMain { t.addKeyfiles(keyfiles) }
        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitStatus("Wrong password", 60)
        onMain { t.clearKeyfiles() }
        onMain { t.addKeyfiles(extraKeys) }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)

        onMain { t.selectTab(3) }
        onMain { t.lockSession() }
        waitStatus("Dismounted", 30)

        onMain { t.selectTab(0) }
        onMain { t.selectContainer(basketDest) }
        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.setVolumePim("1") }
        onMain { t.addKeyfiles(extraKeys) }
        onMain { t.selectTab(3) }
        onMain { t.removeAllKeyfiles() }
        waitStatus("Removed all keyfiles from volume", 60)

        onMain { t.selectTab(0) }
        onMain { t.clearKeyfiles() }
        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)

        onMain { t.selectTab(3) }
        onMain { t.lockSession() }
        waitStatus("Dismounted", 30)

        onMain { t.selectTab(0) }
        onMain { t.selectContainer(basketDest) }
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.addKeyfiles(keyfiles) }
        onMain { t.restoreHeader(headerBak) }
        waitStatus("Restored volume header", 60)

        onMain { t.setVolumePassword(changedPassword) }
        onMain { t.openVolume() }
        waitStatus("Wrong password", 60)
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)
        onMain { t.selectTab(3) }
        onMain { t.showVolumeProperties() }
        waitUntil(15) {
            let info = t.volumeInfo() ?? t.status()
            return info.contains("AES(Twofish(Serpent))") && info.contains("HMAC-SHA-512")
        }
        let memoRestored = work.appendingPathComponent("memo-after-restore.txt")
        let memoRestoredName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("MEMO") }!
        XCTAssertTrue(onMainValue { t.exportNamed(memoRestoredName, memoRestored) })
        XCTAssertEqual(try String(contentsOf: memoRestored, encoding: .utf8), memoText)
        onMain { t.openDir("INBOX") }
        waitUntil(15) {
            t.entryNames().contains { $0.localizedCaseInsensitiveContains("NEST") }
        }

        onMain { t.selectTab(3) }
        onMain { t.lockSession() }
        waitStatus("Dismounted", 30)

        onMain { t.selectTab(0) }
        onMain { t.selectContainer(basketDest) }
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.addKeyfiles(keyfiles) }
        onMain { t.selectTab(3) }
        onMain { t.restoreEmbedded() }
        waitStatus("Restored from embedded backup header", 60)

        onMain { t.selectTab(0) }
        onMain { t.setVolumePassword(basketPassword!) }
        onMain { t.setVolumePim("1") }
        onMain { t.setUseBackupHeader(true) }
        onMain { t.setReadOnly(true) }
        onMain { t.openVolume() }
        waitStatus("Mounted in this app", 180)

        onMain { t.selectTab(2) }
        onMain { t.wipeFreeSpace() }
        waitStatus("Read-only volumes refuse", 30)

        let memoHashName = onMainValue { t.entryNames() }.first { $0.localizedCaseInsensitiveContains("MEMO") }!
        onMain { t.hashSelected(memoHashName) }
        waitStatus("SHA-256 in volume", 60)

        onMain { t.selectTab(3) }
        waitUntil(8) { t.pimEstimate().contains("500") }
        onMain { t.fireIdleTimeout() }
        waitStatus("Idle timeout", 15)

        XCTAssertFalse(onMainValue { t.status() }.localizedCaseInsensitiveContains("Check for updates"))
        try? FileManager.default.removeItem(at: work)
    }

    /// Empty tabs only: no password, no open folder listing. For GitHub README.
    func testPublishTabScreenshots() {
        continueAfterFailure = false
        waitReady()
        let t = VcPortTesting.shared
        let dir = shotDir()

        onMain { t.selectTab(0) }
        pump(0.8)
        saveShot(dir, "ios-01-volume.png")

        onMain { t.selectTab(1) }
        pump(0.8)
        saveShot(dir, "ios-03-create.png")

        onMain { t.selectTab(2) }
        pump(0.8)
        saveShot(dir, "ios-05-mounted.png")

        onMain { t.selectTab(3) }
        pump(0.8)
        saveShot(dir, "ios-04-tools.png")
    }

    private func shotDir() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("github-shots", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func saveShot(_ dir: URL, _ name: String) {
        let image = onMainValue { () -> UIImage? in
            let window = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap(\.windows)
                .first { $0.isKeyWindow }
            guard let window else { return nil }
            let format = UIGraphicsImageRendererFormat()
            format.scale = window.screen.scale
            format.opaque = true
            let renderer = UIGraphicsImageRenderer(bounds: window.bounds, format: format)
            return renderer.image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
        }
        XCTAssertNotNil(image, "no key window for \(name)")
        guard let data = image?.pngData() else {
            XCTFail("png \(name)")
            return
        }
        XCTAssertGreaterThan(data.count, 20_000, "\(name) too small")
        do {
            try data.write(to: dir.appendingPathComponent(name))
        } catch {
            XCTFail("write \(name): \(error)")
        }
    }

    private func waitReady() {
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline {
            if VcPortTesting.shared.ready { return }
            pump(0.1)
        }
        XCTFail("ContentView session hooks were not ready")
    }

    private func waitStatus(_ needle: String, _ timeout: TimeInterval) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if VcPortTesting.shared.status().localizedCaseInsensitiveContains(needle) {
                return
            }
            pump(0.2)
        }
        XCTFail("Timed out waiting for status containing '\(needle)'. Last status: '\(VcPortTesting.shared.status())'")
    }

    private func waitUntil(_ timeout: TimeInterval, _ pred: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if pred() { return }
            pump(0.2)
        }
        XCTFail("Timed out. Last status: '\(VcPortTesting.shared.status())' entries: \(VcPortTesting.shared.entryNames())")
    }

    private func pump(_ seconds: TimeInterval) {
        RunLoop.current.run(until: Date().addingTimeInterval(seconds))
    }

    private func onMain(_ body: () -> Void) {
        if Thread.isMainThread {
            body()
        } else {
            DispatchQueue.main.sync(execute: body)
        }
    }

    private func onMainValue<T>(_ body: () -> T) -> T {
        if Thread.isMainThread {
            return body()
        }
        return DispatchQueue.main.sync(execute: body)
    }

    private func snapshotKeyfiles(into dest: URL) -> [URL] {
        try? FileManager.default.createDirectory(at: dest, withIntermediateDirectories: true)
        var out: [URL] = []
        for src in onMainValue({ VcPortTesting.shared.keyfileURLs() }) {
            guard FileManager.default.fileExists(atPath: src.path) else { continue }
            let copy = dest.appendingPathComponent(src.lastPathComponent)
            try? FileManager.default.removeItem(at: copy)
            do {
                try FileManager.default.copyItem(at: src, to: copy)
                out.append(copy)
            } catch {
                continue
            }
        }
        return out
    }

    private func write(_ url: URL, _ data: Data) -> URL {
        try? data.write(to: url)
        return url
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
