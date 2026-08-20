import CryptoKit
import Foundation
import XCTest
@testable import VCPort

/// Sprint 11: open official desktop VeraCrypt volumes and a host-engine volume.
final class DesktopCompatVolumeTests: XCTestCase {
    func testOpenDesktopCreatedVolumes() throws {
        let dir = desktopDir()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let names = [
            "aes-sha512-pim-kf.json",
            "cascade-sha512.json",
            "aes-sha256.json",
            "engine-made.json",
            "hidden.json"
        ]
        var opened = 0
        for name in names {
            let metaURL = dir.appendingPathComponent(name)
            guard FileManager.default.fileExists(atPath: metaURL.path) else {
                continue
            }
            let meta = try JSONSerialization.jsonObject(with: Data(contentsOf: metaURL)) as! [String: Any]
            try openOne(dir: dir, meta: meta)
            opened += 1
        }
        try XCTSkipUnless(opened > 0, "desktop volumes not copied onto this simulator yet")
        XCTAssertGreaterThanOrEqual(opened, 4, "expected AES+keyfile, cascade, sha256, engine")
    }

    private func openOne(dir: URL, meta: [String: Any]) throws {
        let volumeName = meta["volume"] as? String ?? ""
        let volume = dir.appendingPathComponent(volumeName)
        XCTAssertTrue(FileManager.default.fileExists(atPath: volume.path), "\(volumeName) missing")
        let password = meta["password"] as! String
        let pim = (meta["pim"] as? NSNumber)?.int32Value ?? 1
        let keyNames = (meta["keyfiles"] as? [Any])?.compactMap { $0 as? String } ?? []
        let keyfiles = keyNames.map { dir.appendingPathComponent($0).path }
        for kf in keyfiles {
            XCTAssertTrue(FileManager.default.fileExists(atPath: kf), "keyfile missing \(kf)")
        }
        var err: Int32 = 0
        guard let handle = VcMobileBridge.open(
            path: volume.path,
            password: password,
            pim: pim,
            keyfiles: keyfiles,
            readOnly: true,
            error: &err
        ) else {
            XCTFail("\(volumeName) must open: \(err)")
            return
        }
        let info = VcMobileBridge.volumeInfo(handle) ?? ""
        if let cipher = meta["cipher"] as? String, !cipher.isEmpty {
            XCTAssertTrue(info.contains(cipher), "\(volumeName) cipher \(info)")
        }
        if let kdf = meta["kdf"] as? String, !kdf.isEmpty {
            XCTAssertTrue(info.contains(kdf), "\(volumeName) kdf \(info)")
        }
        try checkFiles(handle: handle, files: meta["files"] as! [String: String], label: "\(volumeName) outer")
        VcMobileBridge.close(handle)

        if let hiddenPassword = meta["hidden_password"] as? String, !hiddenPassword.isEmpty {
            var hiddenErr: Int32 = 0
            let hiddenPim = (meta["hidden_pim"] as? NSNumber)?.int32Value ?? pim
            guard let hidden = VcMobileBridge.open(
                path: volume.path,
                password: hiddenPassword,
                pim: hiddenPim,
                keyfiles: keyfiles,
                readOnly: true,
                error: &hiddenErr
            ) else {
                XCTFail("\(volumeName) hidden must open: \(hiddenErr)")
                return
            }
            try checkFiles(handle: hidden, files: meta["hidden_files"] as! [String: String], label: "\(volumeName) hidden")
            VcMobileBridge.close(hidden)
        }
    }

    private func checkFiles(handle: OpaquePointer, files: [String: String], label: String) throws {
        let listed = VcMobileBridge.listRoot(handle).map(\.name)
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("desktop-open", isDirectory: true)
        try? FileManager.default.removeItem(at: out)
        try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        for (name, want) in files {
            XCTAssertTrue(listed.contains(name), "\(label) \(name) missing: \(listed)")
            let dest = out.appendingPathComponent(name)
            XCTAssertEqual(0, VcMobileBridge.exportFile(handle, name: name, dest: dest.path))
            let data = try Data(contentsOf: dest)
            XCTAssertEqual(want, sha256(data), "\(label) \(name) hash")
            if name.caseInsensitiveCompare("PHOTO.JPG") == .orderedSame {
                XCTAssertEqual(32 * 1024, data.count, "\(label) PHOTO.JPG size")
            }
        }
        try? FileManager.default.removeItem(at: out)
    }

    private func desktopDir() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("desktop", isDirectory: true)
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
