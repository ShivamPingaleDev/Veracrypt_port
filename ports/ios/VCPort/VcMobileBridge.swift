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
}
