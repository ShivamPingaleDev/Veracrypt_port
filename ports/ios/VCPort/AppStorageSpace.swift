import Foundation

/// App-cache copy of a Files/SAF container. Phone volumes are copied into
/// temporary storage; name the shortage instead of a vague "no space" path.
enum AppStorageSpace {
    static let headroom: UInt64 = 32 * 1024 * 1024
    static let unreadable =
        "Could not copy the container into app storage. Pick it again from Files."
    static var lastError = ""

    static func freeBytes() -> UInt64 {
        let url = FileManager.default.temporaryDirectory
        let keys: Set<URLResourceKey> = [
            .volumeAvailableCapacityForImportantUsageKey,
            .volumeAvailableCapacityKey
        ]
        guard let values = try? url.resourceValues(forKeys: keys) else { return 0 }
        if let important = values.volumeAvailableCapacityForImportantUsage, important > 0 {
            return UInt64(important)
        }
        if let cap = values.volumeAvailableCapacity, cap > 0 {
            return UInt64(cap)
        }
        return 0
    }

    static func appStorageNeed(payload: UInt64) -> UInt64 {
        payload + headroom
    }

    static func notEnoughAppStorage(payload: UInt64, free: UInt64) -> String {
        let need = appStorageNeed(payload: payload)
        return "Not enough free space in app storage. Needs \(SizeUnit.formatBytes(need)); this phone has \(SizeUnit.formatBytes(free))."
    }

    static func shortageMessage(payload: UInt64) -> String? {
        guard payload > 0 else { return nil }
        let free = freeBytes()
        if free < appStorageNeed(payload: payload) {
            return notEnoughAppStorage(payload: payload, free: free)
        }
        return nil
    }

    static func fileSize(_ url: URL) -> UInt64 {
        let n = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        return UInt64(max(n, 0))
    }
}
