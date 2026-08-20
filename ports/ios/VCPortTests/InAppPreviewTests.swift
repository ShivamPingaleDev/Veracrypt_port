import Foundation
import XCTest
@testable import VCPort

final class InAppPreviewTests: XCTestCase {
    func testKindsStayInsideApp() {
        XCTAssertEqual(InAppPreview.kind(of: "NOTE.TXT"), .text)
        XCTAssertEqual(InAppPreview.kind(of: "photo.PNG"), .image)
        XCTAssertEqual(InAppPreview.kind(of: "doc.pdf"), .pdf)
        XCTAssertEqual(InAppPreview.kind(of: "clip.mp3"), .audio)
        XCTAssertEqual(InAppPreview.kind(of: "clip.mp4"), .video)
        XCTAssertEqual(InAppPreview.kind(of: "report.docx"), .unsupported)
    }

    func testPreviewTextFromVolume() {
        fillEntropy()
        let work = FileManager.default.temporaryDirectory.appendingPathComponent("preview-sim", isDirectory: true)
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
        guard let handle = VcMobileBridge.open(
            path: volume.path,
            password: pw,
            pim: 1,
            keyfiles: [],
            error: &err
        ) else {
            XCTFail("preview volume must open: \(err)")
            return
        }
        let note = work.appendingPathComponent("NOTE.TXT")
        try? Data("preview-in-app-ok\n".utf8).write(to: note)
        XCTAssertEqual(0, VcMobileBridge.importFile(handle, destDir: "/", src: note.path, destName: "NOTE.TXT"))
        guard let dest = InAppPreview.materialize(handle: handle, volumePath: "NOTE.TXT", name: "NOTE.TXT") else {
            XCTFail("materialize")
            VcMobileBridge.close(handle)
            return
        }
        XCTAssertEqual("preview-in-app-ok\n", InAppPreview.readText(dest))
        VcMobileBridge.close(handle)
        InAppPreview.wipe()
        let previewDir = FileManager.default.temporaryDirectory.appendingPathComponent("preview", isDirectory: true)
        XCTAssertFalse(FileManager.default.fileExists(atPath: previewDir.path))
        try? FileManager.default.removeItem(at: work)
    }

    private func fillEntropy() {
        VcMobileBridge.resetEntropy()
        let sample = Data(repeating: 0x5A, count: 32)
        for _ in 0..<320 {
            VcMobileBridge.addEntropy(sample)
        }
    }
}
