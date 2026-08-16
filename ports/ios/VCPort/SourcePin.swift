import Foundation

/// Baked-in public source pin from Info.plist (ports/version.json via
/// sync_source_pin.py). This IPA never downloads or installs VeraCrypt source.
/// Official git and GitHub latest-release URL are hardcoded. See ports/UPSTREAM.md.
enum SourcePin {
    static let repo = (Bundle.main.object(forInfoDictionaryKey: "VCPortSourceRepo") as? String)
        ?? "https://github.com/ShivamPingaleDev/Veracrypt_port"
    static let manifestURL = URL(
        string: (Bundle.main.object(forInfoDictionaryKey: "VCPortUpdateManifest") as? String)
            ?? "https://raw.githubusercontent.com/ShivamPingaleDev/Veracrypt_port/master/ports/version.json"
    )!
    static var localVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"
    }
    static var upstreamVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "VCPortUpstreamVersion") as? String ?? ""
    }
    static var upstreamCommit: String {
        Bundle.main.object(forInfoDictionaryKey: "VCPortUpstreamCommit") as? String ?? ""
    }
    static var upstreamGit: String {
        (Bundle.main.object(forInfoDictionaryKey: "VCPortUpstreamGit") as? String)
            ?? "https://github.com/veracrypt/VeraCrypt.git"
    }
    static var upstreamReleases: URL {
        URL(
            string: (Bundle.main.object(forInfoDictionaryKey: "VCPortUpstreamReleases") as? String)
                ?? "https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest"
        )!
    }
    static var upstreamTag: String {
        (Bundle.main.object(forInfoDictionaryKey: "VCPortUpstreamTag") as? String) ?? ""
    }

    struct CheckResult {
        var newer: Bool
        var remoteVersion: String
        var notes: String
        var downloadURL: String
        var apkSha256: String
        var remoteUpstreamCommit: String
        var sourceMoved: Bool
        var officialNewer: Bool
        var officialVersion: String
        var sourceDegraded: Bool
        var sourceWarning: String
    }

    enum TrustedNet {
        static let windowSeconds: TimeInterval = 20
        static let maxBody = 64 * 1024
        static let githubStatus = URL(string: "https://www.githubstatus.com/api/v2/status.json")!

        static func allow(_ raw: String) -> Bool {
            guard let u = URL(string: raw), u.scheme == "https" else { return false }
            if u.user != nil || u.password != nil { return false }
            if let port = u.port, port != 443 { return false }
            let host = (u.host ?? "").lowercased()
            let path = u.path
            switch host {
            case "raw.githubusercontent.com":
                return path.hasPrefix("/ShivamPingaleDev/Veracrypt_port/") &&
                    path.hasSuffix("/ports/version.json")
            case "api.github.com":
                return path == "/repos/veracrypt/VeraCrypt/releases/latest"
            case "www.githubstatus.com":
                return path == "/api/v2/status.json"
            default:
                return false
            }
        }
    }

    static func compare(_ a: String, _ b: String) -> Int {
        let pa = a.split { $0 == "." || $0 == "-" }.compactMap { Int($0) }
        let pb = b.split { $0 == "." || $0 == "-" }.compactMap { Int($0) }
        let n = max(pa.count, pb.count)
        for i in 0..<n {
            let x = i < pa.count ? pa[i] : 0
            let y = i < pb.count ? pb[i] : 0
            if x != y { return x < y ? -1 : 1 }
        }
        return 0
    }

    static func versionFromVeraCryptTag(_ tag: String) -> String {
        var t = tag.trimmingCharacters(in: .whitespacesAndNewlines)
        for prefix in ["VeraCrypt_", "VeraCrypt-", "VeraCrypt "] {
            if t.hasPrefix(prefix) {
                t = String(t.dropFirst(prefix.count)).trimmingCharacters(in: .whitespaces)
                break
            }
        }
        return t.split(whereSeparator: { $0.isWhitespace }).first.map(String.init) ?? ""
    }

    static func describeBuild() -> String {
        let short = upstreamCommit.prefix(12)
        return "This build is VC Port \(localVersion), VeraCrypt \(upstreamVersion) (\(short), \(upstreamTag)). Source: \(repo) Official: \(upstreamGit)"
    }
}
