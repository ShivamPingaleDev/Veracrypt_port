import Foundation
import XCTest
@testable import VCPort

/// iOS has no whole-disk USB. This UI session opens a file container and
/// View in app. It does not scan USB disks.
final class OtgAbsentAndPreviewTests: XCTestCase {
    func testNoWholeDiskUsbAndViewInApp() {
        continueAfterFailure = false
        waitReady()
        let t = VcPortTesting.shared
        t.skipSystemPickers = true
        onMain { t.lockSession() }
        pump(1.0)
        VcMobileBridge.resetEntropy()
        onMain { t.fillEntropy() }

        XCTAssertFalse(FossConfig.enableOtgDisk)
        XCTAssertFalse(onMainValue { t.otgDiskEnabled() })

        onMain { t.selectTab(0) }
        pump(2.0)

        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let work = docs.appendingPathComponent("otg-absent-ui", isDirectory: true)
        try? FileManager.default.removeItem(at: work)
        try? FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)
        let volume = work.appendingPathComponent("preview.hc")
        let pw = "vcport-preview-ok"
        XCTAssertEqual(
            0,
            VcMobileBridge.createVolume(
                path: volume.path,
                password: pw,
                pim: 1,
                sizeBytes: 2 * 1024 * 1024,
                cipher: "AES",
                kdf: "HMAC-SHA-512",
                keyfiles: []
            )
        )
        var err: Int32 = 0
        guard let seed = VcMobileBridge.open(
            path: volume.path,
            password: pw,
            pim: 1,
            keyfiles: [],
            error: &err
        ) else {
            XCTFail("seed open: \(err)")
            return
        }
        let note = work.appendingPathComponent("NOTE.TXT")
        try? Data("preview-in-app-ok\n".utf8).write(to: note)
        XCTAssertEqual(0, VcMobileBridge.importFile(seed, destDir: "/", src: note.path, destName: "NOTE.TXT"))
        VcMobileBridge.close(seed)

        onMain { t.selectContainer(volume) }
        onMain { t.setVolumePassword(pw) }
        onMain { t.setVolumePim("1") }
        onMain { t.openVolume() }
        waitUntil(90) {
            onMainValue { t.entryNames() }.contains { $0.compare("NOTE.TXT", options: .caseInsensitive) == .orderedSame }
        }
        onMain { t.selectTab(2) }
        pump(2.0)
        onMain { t.selectNames(["NOTE.TXT"]) }
        onMain { t.startPreview() }
        waitUntil(30) {
            onMainValue { t.previewName() }?.compare("NOTE.TXT", options: .caseInsensitive) == .orderedSame
        }
        pump(3.0)
        XCTAssertEqual(onMainValue { t.previewName() }, "NOTE.TXT")
    }

    private func waitReady() {
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline {
            if VcPortTesting.shared.ready { return }
            pump(0.1)
        }
        XCTFail("ContentView session hooks were not ready")
    }

    private func waitUntil(_ timeout: TimeInterval, _ pred: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if pred() { return }
            pump(0.2)
        }
        XCTFail("Timed out. Last status: '\(VcPortTesting.shared.status())' entries: \(VcPortTesting.shared.entryNames()) preview: \(VcPortTesting.shared.previewName() ?? "nil")")
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
}
