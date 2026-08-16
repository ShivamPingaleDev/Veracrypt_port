import Foundation

struct VaultEntry: Identifiable {
    let name: String
    let isDir: Bool
    let size: UInt64
    let dosDate: UInt16
    let dosTime: UInt16
    var id: String { name }
}

struct VcStatusCode: Error {
    let rawValue: Int32
}

enum VcMobileBridge {
    static func startCpu() {
        vc_runtime_start()
    }

    static func open(path: String, password: String, pim: Int32, keyfiles: [String], useBackupHeader: Bool = false, readOnly: Bool = false, protectHidden: Bool = false, hiddenPassword: String = "", hiddenPim: Int32 = 0, error: UnsafeMutablePointer<Int32>) -> OpaquePointer? {
        startCpu()
        path.withCString { cPath in
            password.withCString { cPassword in
                hiddenPassword.withCString { cHidden in
                withCStringArray(keyfiles) { pointer, count in
                    var options = VcOpenOptions()
                    options.path = cPath
                    options.password = cPassword
                    options.password_len = password.utf8.count
                    options.pim = pim
                    options.use_backup_header = useBackupHeader ? 1 : 0
                    options.keyfiles = pointer
                    options.keyfile_count = count
                    options.read_only = readOnly ? 1 : 0
                    options.protect_hidden = protectHidden ? 1 : 0
                    options.hidden_password = cHidden
                    options.hidden_password_len = hiddenPassword.utf8.count
                    options.hidden_pim = hiddenPim
                    return vc_open(&options, error)
                }
                }
            }
        }
    }

    private static func withCStringArray<R>(_ strings: [String], _ body: (UnsafePointer<UnsafePointer<CChar>?>?, Int) -> R) -> R {
        if strings.isEmpty {
            return body(nil, 0)
        }
        var heap = strings.map { strdup($0) }
        defer { heap.forEach { free($0) } }
        var ptrs: [UnsafePointer<CChar>?] = heap.map { ptr in
            ptr.map { UnsafePointer($0) }
        }
        return ptrs.withUnsafeBufferPointer { buf in
            body(buf.baseAddress, strings.count)
        }
    }

    static func close(_ handle: OpaquePointer) {
        vc_close(handle)
    }

    static func size(_ handle: OpaquePointer) -> UInt64 {
        vc_size(handle)
    }

    static func listDir(_ handle: OpaquePointer, path: String = "/", offset: Int32 = 0) -> Result<[VaultEntry], VcStatusCode> {
        let cap = Int32(VC_LIST_UI_MAX)
        var entries = Array(repeating: VcDirEntry(), count: Int(cap) + 1)
        let count = path.withCString { cPath in
            vc_list_dir_from(handle, cPath, &entries, cap + 1, offset)
        }
        if count < 0 {
            return .failure(VcStatusCode(rawValue: count))
        }
        var truncated = false
        var n = Int(count)
        if n > Int(cap) {
            n = Int(cap)
            truncated = true
        }
        var listed = (0..<n).compactMap { index -> VaultEntry? in
            let entry = entries[index]
            let name = withUnsafeBytes(of: entry.name) { raw in
                String(cString: raw.bindMemory(to: CChar.self).baseAddress!)
            }
            guard !name.isEmpty else { return nil }
            return VaultEntry(name: name, isDir: entry.is_dir != 0, size: entry.size, dosDate: entry.dos_date, dosTime: entry.dos_time)
        }
        if truncated {
            listed.append(VaultEntry(name: "!truncated!", isDir: false, size: UInt64(cap), dosDate: 0, dosTime: 0))
        }
        return .success(listed)
    }

    static func listRoot(_ handle: OpaquePointer) -> [VaultEntry] {
        (try? listDir(handle, path: "/").get()) ?? []
    }

    static func exportFile(_ handle: OpaquePointer, name: String, dest: String) -> Int32 {
        name.withCString { cName in
            dest.withCString { cDest in
                vc_export_file(handle, cName, cDest)
            }
        }
    }

    static func importFile(_ handle: OpaquePointer, destDir: String, src: String, destName: String) -> Int32 {
        destDir.withCString { cDir in
            src.withCString { cSrc in
                destName.withCString { cName in
                    vc_import_file(handle, cDir, cSrc, cName)
                }
            }
        }
    }

    static func deleteFile(_ handle: OpaquePointer, path: String) -> Int32 {
        path.withCString { cPath in
            vc_delete_file(handle, cPath)
        }
    }

    static func mkdir(_ handle: OpaquePointer, parent: String, name: String) -> Int32 {
        parent.withCString { cParent in
            name.withCString { cName in
                vc_mkdir(handle, cParent, cName)
            }
        }
    }

    static func rmdir(_ handle: OpaquePointer, path: String) -> Int32 {
        path.withCString { cPath in
            vc_rmdir(handle, cPath)
        }
    }

    static func renameFile(_ handle: OpaquePointer, path: String, newName: String) -> Int32 {
        path.withCString { cPath in
            newName.withCString { cName in
                vc_rename(handle, cPath, cName)
            }
        }
    }

    static func wipeFreeSpace(_ handle: OpaquePointer) -> Int32 {
        vc_wipe_free_space(handle)
    }

    static func wrapFile(src: String, dest: String, password: String, originalName: String) -> Int32 {
        src.withCString { cSrc in
            dest.withCString { cDest in
                password.withCString { cPassword in
                    originalName.withCString { cName in
                        vc_wrap_file(cSrc, cDest, cPassword, password.utf8.count, cName)
                    }
                }
            }
        }
    }

    static func unwrapFile(src: String, destDir: String, password: String) -> String? {
        var out = [CChar](repeating: 0, count: 1024)
        let rc = src.withCString { cSrc in
            destDir.withCString { cDir in
                password.withCString { cPassword in
                    vc_unwrap_file(cSrc, cDir, cPassword, password.utf8.count, &out, 1024)
                }
            }
        }
        guard rc == 0 else { return nil }
        return String(cString: out)
    }

    static func isWrap(_ path: String) -> Bool {
        path.withCString { vc_is_wrap($0) != 0 }
    }

    static func generatePassword(length: Int32 = 64) -> String? {
        var buf = [CChar](repeating: 0, count: 80)
        let n = vc_generate_password(&buf, 80, length)
        defer {
            buf.withUnsafeMutableBytes { raw in
                raw.initializeMemory(as: UInt8.self, repeating: 0)
            }
        }
        guard n >= 16 else { return nil }
        return String(cString: buf)
    }

    static let ciphers = [
        "AES",
        "Serpent",
        "Twofish",
        "Camellia",
        "Kuznyechik",
        "AES(Twofish)",
        "AES(Twofish(Serpent))",
        "Camellia(Kuznyechik)",
        "Camellia(Serpent)",
        "Kuznyechik(AES)",
        "Kuznyechik(Serpent(Camellia))",
        "Kuznyechik(Twofish)",
        "Serpent(AES)",
        "Serpent(Twofish(AES))",
        "Twofish(Serpent)"
    ]
    static let kdfs = [
        "HMAC-SHA-512",
        "HMAC-SHA-256",
        "HMAC-BLAKE2s-256",
        "HMAC-Whirlpool",
        "HMAC-Streebog",
        "Argon2"
    ]
    static let defaultCipher = "AES(Twofish(Serpent))"
    static let defaultKdf = "HMAC-SHA-512"

    static func addEntropy(_ data: Data) {
        data.withUnsafeBytes { raw in
            guard let base = raw.baseAddress else { return }
            vc_entropy_add(base, raw.count)
        }
    }

    static func entropyPercent() -> Int32 {
        vc_entropy_percent()
    }

    static func resetEntropy() {
        vc_entropy_reset()
    }

    static func createVolume(
        path: String,
        password: String,
        pim: Int32,
        sizeBytes: UInt64,
        cipher: String,
        kdf: String,
        keyfiles: [String],
        hiddenPassword: String = "",
        hiddenPim: Int32 = 0,
        hiddenSizeBytes: UInt64 = 0,
        hiddenKeyfiles: [String] = []
    ) -> Int32 {
        startCpu()
        path.withCString { cPath in
            password.withCString { cPassword in
                cipher.withCString { cCipher in
                    kdf.withCString { cKdf in
                        hiddenPassword.withCString { cHidden in
                            withCStringArray(keyfiles) { pointer, count in
                                withCStringArray(hiddenKeyfiles) { hiddenPointer, hiddenCount in
                                    var options = VcCreateOptions()
                                    options.path = cPath
                                    options.password = cPassword
                                    options.password_len = password.utf8.count
                                    options.pim = pim
                                    options.size_bytes = sizeBytes
                                    options.cipher = cCipher
                                    options.kdf = cKdf
                                    options.keyfiles = pointer
                                    options.keyfile_count = count
                                    options.hidden_size_bytes = hiddenSizeBytes
                                    options.hidden_password = cHidden
                                    options.hidden_password_len = hiddenPassword.utf8.count
                                    options.hidden_pim = hiddenPim
                                    options.hidden_keyfiles = hiddenPointer
                                    options.hidden_keyfile_count = hiddenCount
                                    return vc_create_volume(&options)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static func changeHeader(
        path: String,
        password: String,
        pim: Int32,
        keyfiles: [String],
        backup: Bool,
        newPassword: String,
        newPim: Int32,
        newKdf: String,
        newKeyfiles: [String]
    ) -> Int32 {
        path.withCString { cPath in
            password.withCString { cPassword in
                newPassword.withCString { cNew in
                    newKdf.withCString { cKdf in
                        withCStringArray(keyfiles) { pointer, count in
                            withCStringArray(newKeyfiles) { newPointer, newCount in
                                var options = VcChangeHeaderOptions()
                                options.path = cPath
                                options.password = cPassword
                                options.password_len = password.utf8.count
                                options.pim = pim
                                options.use_backup_header = backup ? 1 : 0
                                options.new_password = cNew
                                options.new_password_len = newPassword.utf8.count
                                options.new_pim = newPim
                                options.new_kdf = cKdf
                                options.keyfiles = pointer
                                options.keyfile_count = count
                                options.new_keyfiles = newPointer
                                options.new_keyfile_count = newCount
                                return vc_change_header(&options)
                            }
                        }
                    }
                }
            }
        }
    }

    static func backupHeaders(volumePath: String, backupPath: String, password: String, pim: Int32, keyfiles: [String]) -> Int32 {
        volumePath.withCString { cVol in
            backupPath.withCString { cBak in
                password.withCString { cPassword in
                    withCStringArray(keyfiles) { pointer, count in
                        vc_backup_headers(cVol, cBak, cPassword, password.utf8.count, pim, pointer, count)
                    }
                }
            }
        }
    }

    static func restoreHeaders(volumePath: String, backupPath: String, password: String, pim: Int32, keyfiles: [String]) -> Int32 {
        volumePath.withCString { cVol in
            backupPath.withCString { cBak in
                password.withCString { cPassword in
                    withCStringArray(keyfiles) { pointer, count in
                        vc_restore_headers(cVol, cBak, cPassword, password.utf8.count, pim, pointer, count)
                    }
                }
            }
        }
    }

    static func generateKeyfile(path: String, size: Int32 = 128) -> Int32 {
        path.withCString { vc_generate_keyfile($0, size > 0 ? Int(size) : 128) }
    }

    static func volumeInfo(_ handle: OpaquePointer) -> String? {
        var buf = [CChar](repeating: 0, count: 512)
        guard vc_volume_info(handle, &buf, 512) == 0 else { return nil }
        return String(cString: buf)
    }

    static func protectionTriggered(_ handle: OpaquePointer) -> Bool {
        vc_protection_triggered(handle) != 0
    }

    static func benchmark() -> String {
        startCpu()
        var buf = [CChar](repeating: 0, count: 2048)
        guard vc_benchmark(&buf, 2048) == 0 else { return "Benchmark failed." }
        return String(cString: buf)
    }

    static func testVectors() -> Int32 {
        vc_test_vectors()
    }

    static func resetProgress() {
        vc_progress_reset()
    }

    static func setProgress(_ percent: Int32, phase: String) {
        phase.withCString { vc_progress_set(percent, $0) }
    }

    static func progressPercent() -> Int {
        Int(vc_progress_percent())
    }

    static func progressPhase() -> String {
        var buf = [CChar](repeating: 0, count: 96)
        vc_progress_phase(&buf, 96)
        return String(cString: buf)
    }
}
