import Foundation

struct VaultEntry: Identifiable {
    let name: String
    let isDir: Bool
    let size: UInt64
    var id: String { name }
}

enum VcMobileBridge {
    static func open(path: String, password: String, pim: Int32, error: UnsafeMutablePointer<Int32>) -> OpaquePointer? {
        path.withCString { cPath in
            password.withCString { cPassword in
                var options = VcOpenOptions()
                options.path = cPath
                options.password = cPassword
                options.password_len = password.utf8.count
                options.pim = pim
                options.use_backup_header = 0
                options.keyfiles = nil
                options.keyfile_count = 0
                return vc_open(&options, error)
            }
        }
    }

    static func close(_ handle: OpaquePointer) {
        vc_close(handle)
    }

    static func size(_ handle: OpaquePointer) -> UInt64 {
        vc_size(handle)
    }

    static func listRoot(_ handle: OpaquePointer) -> [VaultEntry] {
        var entries = Array(repeating: VcDirEntry(), count: 128)
        let count = vc_list_root(handle, &entries, 128)
        guard count > 0 else { return [] }
        return (0..<Int(count)).compactMap { index in
            let entry = entries[index]
            let name = withUnsafeBytes(of: entry.name) { raw in
                String(cString: raw.bindMemory(to: CChar.self).baseAddress!)
            }
            guard !name.isEmpty else { return nil }
            return VaultEntry(name: name, isDir: entry.is_dir != 0, size: entry.size)
        }
    }

    static func exportFile(_ handle: OpaquePointer, name: String, dest: String) -> Int32 {
        name.withCString { cName in
            dest.withCString { cDest in
                vc_export_file(handle, cName, cDest)
            }
        }
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

    static func generatePassword(length: Int32 = 24) -> String? {
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
}
