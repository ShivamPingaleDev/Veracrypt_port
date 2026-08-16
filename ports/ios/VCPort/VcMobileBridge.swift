import Foundation

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

    static func listRoot(_ handle: OpaquePointer) -> [String] {
        var entries = Array(repeating: VcDirEntry(), count: 64)
        let count = vc_list_root(handle, &entries, 64)
        guard count > 0 else { return [] }
        return (0..<Int(count)).compactMap { index in
            withUnsafeBytes(of: entries[index].name) { raw in
                String(cString: raw.bindMemory(to: CChar.self).baseAddress!)
            }
        }
    }
}
