import CryptoKit
import Foundation
import XCTest
@testable import VCPort

/// Sprint 10: create a random VeraCrypt file container on this phone, then
/// open a container the Android emulator made. A host script copies the .hc
/// files between simulators.
final class CrossPhoneVolumeTests: XCTestCase {
    func testCreateRandomVolumeOnIos() {
        fillEntropy()
        let cross = crossDir()
        try? FileManager.default.createDirectory(at: cross, withIntermediateDirectories: true)
        let work = FileManager.default.temporaryDirectory.appendingPathComponent("cross-create", isDirectory: true)
        try? FileManager.default.removeItem(at: work)
        try? FileManager.default.createDirectory(at: work, withIntermediateDirectories: true)

        guard let password = VcMobileBridge.generatePassword() else {
            XCTFail("generatePassword")
            return
        }
        XCTAssertEqual(password.count, 64)

        let memo = Data("ios-memo-ok\n".utf8)
        let photo = Data((0..<(32 * 1024)).map { UInt8(truncatingIfNeeded: $0) })
        let zip = Data([0x50, 0x4B, 0x03, 0x04, 0x14, 0x00])
        let memoFile = work.appendingPathComponent("MEMO.TXT")
        let photoFile = work.appendingPathComponent("PHOTO.JPG")
        let zipFile = work.appendingPathComponent("NOTES.ZIP")
        try? memo.write(to: memoFile)
        try? photo.write(to: photoFile)
        try? zip.write(to: zipFile)

        let volume = cross.appendingPathComponent("ios-made.hc")
        try? FileManager.default.removeItem(at: volume)
        XCTAssertEqual(
            0,
            VcMobileBridge.createVolume(
                path: volume.path,
                password: password,
                pim: 1,
                sizeBytes: 2 * 1024 * 1024,
                cipher: "AES(Twofish(Serpent))",
                kdf: "HMAC-SHA-512",
                keyfiles: []
            )
        )
        var err: Int32 = 0
        guard let handle = VcMobileBridge.open(
            path: volume.path,
            password: password,
            pim: 1,
            keyfiles: [],
            error: &err
        ) else {
            XCTFail("ios create must open: \(err)")
            return
        }
        XCTAssertEqual(0, VcMobileBridge.importFile(handle, destDir: "/", src: memoFile.path, destName: "MEMO.TXT"))
        XCTAssertEqual(0, VcMobileBridge.importFile(handle, destDir: "/", src: photoFile.path, destName: "PHOTO.JPG"))
        XCTAssertEqual(0, VcMobileBridge.importFile(handle, destDir: "/", src: zipFile.path, destName: "NOTES.ZIP"))
        VcMobileBridge.close(handle)

        let meta: [String: Any] = [
            "password": password,
            "pim": 1,
            "cipher": "AES(Twofish(Serpent))",
            "kdf": "HMAC-SHA-512",
            "files": [
                "MEMO.TXT": sha256(memo),
                "PHOTO.JPG": sha256(photo),
                "NOTES.ZIP": sha256(zip)
            ]
        ]
        let json = try! JSONSerialization.data(withJSONObject: meta, options: [.prettyPrinted])
        try? json.write(to: cross.appendingPathComponent("ios-made.json"))
        let size = (try? FileManager.default.attributesOfItem(atPath: volume.path)[.size] as? NSNumber)?.uint64Value ?? 0
        XCTAssertGreaterThanOrEqual(size, 2 * 1024 * 1024)
        try? FileManager.default.removeItem(at: work)
    }

    func testOpenVolumeMadeOnAndroid() throws {
        let volume = crossDir().appendingPathComponent("android-made.hc")
        let metaURL = crossDir().appendingPathComponent("android-made.json")
        try XCTSkipUnless(
            FileManager.default.fileExists(atPath: volume.path) && FileManager.default.fileExists(atPath: metaURL.path),
            "Android-made volume not copied onto this simulator yet"
        )
        let data = try Data(contentsOf: metaURL)
        let meta = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        let password = meta["password"] as! String
        let pim = (meta["pim"] as? NSNumber)?.int32Value ?? 1
        let files = meta["files"] as! [String: String]
        var err: Int32 = 0
        guard let handle = VcMobileBridge.open(
            path: volume.path,
            password: password,
            pim: pim,
            keyfiles: [],
            readOnly: true,
            error: &err
        ) else {
            XCTFail("Android volume must open on iOS: \(err)")
            return
        }
        let listed = VcMobileBridge.listRoot(handle).map(\.name)
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("cross-open", isDirectory: true)
        try? FileManager.default.removeItem(at: out)
        try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        for (name, want) in files {
            XCTAssertTrue(listed.contains(name), "\(name) missing in Android volume: \(listed)")
            let dest = out.appendingPathComponent(name)
            XCTAssertEqual(0, VcMobileBridge.exportFile(handle, name: name, dest: dest.path))
            XCTAssertEqual(want, sha256(try Data(contentsOf: dest)))
        }
        VcMobileBridge.close(handle)
        try? FileManager.default.removeItem(at: out)
    }

    private func crossDir() -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("cross", isDirectory: true)
    }

    private func fillEntropy() {
        VcMobileBridge.resetEntropy()
        let sample = Data(repeating: 0x5A, count: 32)
        for _ in 0..<320 {
            VcMobileBridge.addEntropy(sample)
        }
        XCTAssertEqual(VcMobileBridge.entropyPercent(), 100)
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
